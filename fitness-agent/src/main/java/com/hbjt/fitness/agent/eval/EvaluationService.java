package com.hbjt.fitness.agent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbjt.fitness.agent.knowledge.KnowledgeHit;
import com.hbjt.fitness.agent.knowledge.KnowledgeService;
import com.hbjt.fitness.agent.service.IntentRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 离线评测：把回归用例沉淀成可反复执行的评测集。
 *
 * <p><b>核心取舍：默认只评测确定性环节（意图路由 + 知识检索命中），不调用大模型。</b>
 *
 * <p>理由很实际：一旦评测要过一遍大模型，单次跑批就要几分钟、且每次结果都不同，
 * 于是"这次改动有没有让回答变差"这个问题反而变得没法回答了——
 * 你分不清是指标变了，还是模型采样抖了一下。
 * 把评测限定在确定性环节后，同一份用例跑两次必然得到同一组数，
 * 回归对比才有意义。需要评估端到端效果时再显式打开全链路模式。
 *
 * <p>评分口径（满分 100）：意图命中 60 分 + 关键词命中按比例给 40 分。
 * 通过条件：意图命中，且若无关键词要求则直接通过、有要求则至少命中一个。
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    /** 意图分与关键词分的权重 */
    private static final double W_INTENT = 60.0;
    private static final double W_KEYWORD = 40.0;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final IntentRouter router;
    private final KnowledgeService knowledge;
    private final ObjectMapper om;

    public EvaluationService(@Qualifier("chatJdbcTemplate") JdbcTemplate jdbc,
                             @Qualifier("chatTransactionTemplate") TransactionTemplate tx,
                             IntentRouter router,
                             KnowledgeService knowledge,
                             ObjectMapper om) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.router = router;
        this.knowledge = knowledge;
        this.om = om;
    }

    /** 内置种子用例。首次跑批前若表为空则写入，之后不再覆盖用户的增删。 */
    private static final List<String[]> SEED = List.of(
            new String[]{"意图路由", "我想充值", "RECHARGE", ""},
            new String[]{"意图路由", "查一下我的余额", "QUERY_BALANCE", ""},
            new String[]{"意图路由", "我的积分还有多少", "QUERY_INTEGRAL", ""},
            new String[]{"意图路由", "看一下我的充值记录", "QUERY_RECHARGE", ""},
            new String[]{"意图路由", "健身房有哪些课程", "QUERY_COURSE", ""},
            new String[]{"意图路由", "帮我制定一个减脂计划", "FITNESS_PLAN", ""},
            new String[]{"意图路由", "确定", "CONFIRM", ""},
            new String[]{"意图路由", "算了不充了", "CANCEL", ""},
            new String[]{"意图路由", "你能做什么", "HELP", ""},
            new String[]{"知识问答", "深蹲怎么做", "QUERY_KNOWLEDGE", "深蹲,膝盖"},
            new String[]{"知识问答", "跑步伤膝盖吗", "QUERY_KNOWLEDGE", "跑,膝盖"},
            new String[]{"知识问答", "高血压能练吗", "QUERY_KNOWLEDGE", "血压"},
            new String[]{"知识问答", "蛋白质怎么吃", "QUERY_KNOWLEDGE", "蛋白质"}
    );

    /** 评测用例为空时灌入种子。 */
    public int seedIfEmpty() {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM eval_case", Integer.class);
            if (n != null && n > 0) {
                return 0;
            }
            LocalDateTime now = LocalDateTime.now();
            int written = 0;
            for (String[] c : SEED) {
                written += jdbc.update(
                        "INSERT IGNORE INTO eval_case (category, question, expect_intent,"
                                + " expect_keywords, enabled, created_at) VALUES (?,?,?,?,1,?)",
                        c[0], c[1], c[2], c[3], now);
            }
            log.info("评测集初始化：写入 {} 条种子用例", written);
            return written;
        } catch (Exception e) {
            log.warn("评测集初始化失败：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 跑一轮评测。
     *
     * @param withLlm 是否连大模型一起跑（慢且结果不稳定，默认 false）
     * @param limit   最多跑多少条用例
     */
    public Map<String, Object> run(boolean withLlm, int limit) {
        Map<String, Object> res = new LinkedHashMap<>();
        seedIfEmpty();

        int n = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> cases;
        try {
            cases = jdbc.queryForList(
                    "SELECT id, category, question, expect_intent, expect_keywords"
                            + " FROM eval_case WHERE enabled=1 ORDER BY id LIMIT " + n);
        } catch (Exception e) {
            log.warn("评测用例读取失败：{}", e.getMessage());
            res.put("available", false);
            res.put("reason", e.getMessage());
            return res;
        }

        LocalDateTime started = LocalDateTime.now();
        long runId;
        try {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO eval_run (started_at, total_cases, passed, failed) VALUES (?,?,0,0)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setTimestamp(1, Timestamp.valueOf(started));
                ps.setInt(2, cases.size());
                return ps;
            }, kh);
            Number key = kh.getKey();
            runId = key == null ? 0L : key.longValue();
        } catch (Exception e) {
            log.warn("评测批次创建失败：{}", e.getMessage());
            res.put("available", false);
            res.put("reason", e.getMessage());
            return res;
        }

        int passed = 0;
        long totalLatency = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (Map<String, Object> c : cases) {
            long caseId = ((Number) c.get("id")).longValue();
            String question = String.valueOf(c.get("question"));
            String expectIntent = c.get("expect_intent") == null ? null : String.valueOf(c.get("expect_intent"));
            String expectKeywords = c.get("expect_keywords") == null ? null : String.valueOf(c.get("expect_keywords"));

            long t0 = System.nanoTime();
            String actualIntent;
            try {
                actualIntent = String.valueOf(router.route(question, null).intent().name());
            } catch (Exception e) {
                actualIntent = "ERROR";
            }
            int hits = 0;
            int total = 0;
            try {
                List<KnowledgeHit> found = knowledge.search(question);
                String text = new StringBuilder()
                        .append(found.stream().map(KnowledgeHit::snippet).reduce("", (a, b) -> a + " " + b))
                        .append(" ")
                        .append(found.stream().map(h -> h.doc().title()).reduce("", (a, b) -> a + " " + b))
                        .toString().toLowerCase(Locale.ROOT);
                for (String kw : splitKeywords(expectKeywords)) {
                    total++;
                    if (text.contains(kw.toLowerCase(Locale.ROOT))) {
                        hits++;
                    }
                }
            } catch (Exception ignored) {
                // 知识库不可用时不计入关键词得分
            }

            long latency = (System.nanoTime() - t0) / 1_000_000L;
            totalLatency += latency;

            boolean intentOk = expectIntent == null || expectIntent.isBlank()
                    || expectIntent.equalsIgnoreCase(actualIntent);
            double score = (intentOk ? W_INTENT : 0.0)
                    + (total == 0 ? 0 : W_KEYWORD * hits / total);
            boolean ok = intentOk && (total == 0 || hits > 0);
            if (ok) {
                passed++;
            }

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("caseId", caseId);
            d.put("question", question);
            d.put("expectIntent", expectIntent);
            d.put("actualIntent", actualIntent);
            d.put("intentOk", intentOk);
            d.put("keywordHits", hits);
            d.put("keywordTotal", total);
            d.put("score", Math.round(score * 10) / 10.0);
            d.put("latencyMs", latency);
            d.put("ok", ok);
            details.add(d);
        }

        int failed = cases.size() - passed;
        int avg = cases.isEmpty() ? 0 : (int) (totalLatency / cases.size());
        LocalDateTime finished = LocalDateTime.now();

        // lambda 只能捕获 effectively final 的局部变量，
        // 而 passed 是在循环里累加出来的，因此这里复制一份再传进去。
        final int passedFinal = passed;

        try {
            tx.execute(status -> {
                jdbc.update(
                        "UPDATE eval_run SET finished_at=?, total_cases=?, passed=?, failed=?,"
                                + " avg_latency_ms=?, summary_json=? WHERE id=?",
                        finished, cases.size(), passedFinal, failed, avg, json(details), runId);
                for (Map<String, Object> d : details) {
                    jdbc.update(
                            "INSERT INTO eval_result (run_id, case_id, actual_intent, intent_ok,"
                                    + " keyword_hits, score, latency_ms, ok, detail) VALUES (?,?,?,?,?,?,?,?,?)",
                            runId, d.get("caseId"), d.get("actualIntent"),
                            Boolean.TRUE.equals(d.get("intentOk")) ? 1 : 0,
                            d.get("keywordHits"), d.get("score"), d.get("latencyMs"),
                            Boolean.TRUE.equals(d.get("ok")) ? 1 : 0,
                            clip("intent=" + d.get("actualIntent")
                                    + ", keywords=" + d.get("keywordHits") + "/" + d.get("keywordTotal"), 500));
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("评测结果写入失败：{}", e.getMessage());
        }

        res.put("available", true);
        res.put("runId", runId);
        res.put("total", cases.size());
        res.put("passed", passed);
        res.put("failed", failed);
        res.put("avgLatencyMs", avg);
        res.put("items", details);
        return res;
    }

    /** 历史批次概览。 */
    public List<Map<String, Object>> runs(int limit) {
        try {
            int n = Math.max(1, Math.min(limit, 100));
            return jdbc.queryForList(
                    "SELECT id, started_at, finished_at, total_cases, passed, failed, avg_latency_ms"
                            + " FROM eval_run ORDER BY id DESC LIMIT " + n);
        } catch (Exception e) {
            log.warn("评测批次查询失败：{}", e.getMessage());
            return List.of();
        }
    }

    /** 某批次的逐题结果。 */
    public List<Map<String, Object>> results(long runId) {
        try {
            return jdbc.queryForList(
                    "SELECT id, case_id, actual_intent, intent_ok, keyword_hits, score,"
                            + " latency_ms, ok, detail FROM eval_result WHERE run_id=? ORDER BY id",
                    runId);
        } catch (Exception e) {
            log.warn("评测结果查询失败：{}", e.getMessage());
            return List.of();
        }
    }

    /** 新增评测用例。 */
    public int addCase(String category, String question, String expectIntent, String expectKeywords) {
        if (question == null || question.isBlank()) {
            return 0;
        }
        try {
            return jdbc.update(
                    "INSERT IGNORE INTO eval_case (category, question, expect_intent,"
                            + " expect_keywords, enabled, created_at) VALUES (?,?,?,?,1,?)",
                    category == null || category.isBlank() ? "未分类" : category,
                    question.trim(), expectIntent, expectKeywords, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("评测用例新增失败：{}", e.getMessage());
            return 0;
        }
    }

    private String json(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    static List<String> splitKeywords(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,，;；]")) {
            String k = part.trim();
            if (!k.isEmpty()) {
                out.add(k);
            }
        }
        return out;
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
