package com.hbjt.fitness.agent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库反馈闭环：用户对某条回答按「有用 / 没用」，系统据此调整该文档在检索中的权重。
 *
 * <p>两条关键设计决策：
 *
 * <ol>
 *   <li><b>权重只影响排序，不影响是否入选。</b>
 *       如果让差评把文档踢出答案集，只要有人连点几次「没用」，这条知识就会彻底消失，
 *       系统从此对这类问题不再作答——这是可以被误触甚至恶意利用的。
 *       因此过滤依旧只看相关度阈值，权重只用来调整谁排在前面。</li>
 *   <li><b>差评的权重略大于好评。</b>
 *       好评可能只是"还行"，差评则明确表示"答错了"。
 *       因此一次差评的惩罚（-0.18）大于一次好评的奖励（+0.12）。
 *       同时上下限夹住（0.4 ~ 2.0），单篇文档不会因几次反馈就彻底失效或被顶到天上。</li>
 * </ol>
 *
 * <p>权重缓存 60 秒：反馈是低频操作，没必要每次检索都查库，但也要保证调完权重能较快看到效果。
 */
@Service
public class KnowledgeFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFeedbackService.class);

    /** 权重计算参数 */
    private static final double UP_GAIN = 0.12;
    private static final double DOWN_PENALTY = 0.18;
    private static final double MIN_WEIGHT = 0.4;
    private static final double MAX_WEIGHT = 2.0;

    /** 权重缓存有效期 */
    private static final long CACHE_TTL_MS = 60_000L;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Map<String, Double> cache = new ConcurrentHashMap<>();
    private volatile long cacheLoadedAt = 0L;

    public KnowledgeFeedbackService(@Qualifier("chatJdbcTemplate") JdbcTemplate jdbc,
                                    @Qualifier("chatTransactionTemplate") TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /**
     * 提交一次反馈。
     *
     * @param up true=有用，false=没用
     * @return 计算后的新权重；写入失败返回 {@code null} 且不抛异常（反馈是增值功能，不能拖垮对话）
     */
    public Double vote(String traceId, String sessionKey, String ownerType, String ownerId,
                       String query, String docId, String docTitle, Double score,
                       boolean up, String comment) {
        if (docId == null || docId.isBlank()) {
            return null;
        }
        String vote = up ? "UP" : "DOWN";
        LocalDateTime now = LocalDateTime.now();
        try {
            return tx.execute(status -> {
                jdbc.update(
                        "INSERT INTO knowledge_feedback (trace_id, session_key, owner_type, owner_id,"
                                + " query, doc_id, doc_title, score, vote, comment, created_at)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        traceId, sessionKey, ownerType, ownerId,
                        clip(query, 300), docId, clip(docTitle, 200), score, vote,
                        clip(comment, 500), now);

                Integer ups = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM knowledge_feedback WHERE doc_id=? AND vote='UP'",
                        Integer.class, docId);
                Integer downs = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM knowledge_feedback WHERE doc_id=? AND vote='DOWN'",
                        Integer.class, docId);
                int u = ups == null ? 0 : ups;
                int d = downs == null ? 0 : downs;
                double w = weightOf(u, d);

                jdbc.update(
                        "INSERT INTO knowledge_doc_weight (doc_id, doc_title, up_count, down_count,"
                                + " weight, updated_at) VALUES (?,?,?,?,?,?)"
                                + " ON DUPLICATE KEY UPDATE doc_title=VALUES(doc_title),"
                                + " up_count=VALUES(up_count), down_count=VALUES(down_count),"
                                + " weight=VALUES(weight), updated_at=VALUES(updated_at)",
                        docId, clip(docTitle, 200), u, d, w, now);

                cache.put(docId, w);
                log.info("知识反馈 doc={} vote={} 权重={}", docId, vote, w);
                return w;
            });
        } catch (Exception e) {
            log.warn("知识反馈写入失败（不影响对话）：{}", e.getMessage());
            return null;
        }
    }

    /** 权重公式，并夹在上下限之间。 */
    static double weightOf(int up, int down) {
        double w = 1.0 + UP_GAIN * up - DOWN_PENALTY * down;
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, w));
    }

    /**
     * 文档权重表（带缓存）。检索时用它调整排序。
     *
     * <p>缓存读失败返回空表而不是抛异常：没有权重时文档按 1.0 处理，检索照常工作，
     * 只是少了个性化排序。
     */
    public Map<String, Double> weights() {
        long now = System.currentTimeMillis();
        if (now - cacheLoadedAt < CACHE_TTL_MS && !cache.isEmpty()) {
            return cache;
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT doc_id, weight FROM knowledge_doc_weight");
            Map<String, Double> fresh = new ConcurrentHashMap<>();
            for (Map<String, Object> r : rows) {
                Object w = r.get("weight");
                if (r.get("doc_id") != null && w != null) {
                    fresh.put(String.valueOf(r.get("doc_id")), ((Number) w).doubleValue());
                }
            }
            cache.clear();
            cache.putAll(fresh);
            cacheLoadedAt = now;
        } catch (Exception e) {
            log.warn("权重缓存刷新失败，沿用旧值：{}", e.getMessage());
            cacheLoadedAt = now;   // 避免每次检索都重试，拖慢响应
        }
        return cache;
    }

    /** 各文档的反馈统计，按反馈量降序。 */
    public java.util.List<Map<String, Object>> summary() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            out = jdbc.queryForList(
                    "SELECT doc_id, doc_title, up_count, down_count, weight, updated_at"
                            + " FROM knowledge_doc_weight"
                            + " ORDER BY (up_count + down_count) DESC, doc_id LIMIT 200");
        } catch (Exception e) {
            log.warn("反馈统计查询失败：{}", e.getMessage());
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
