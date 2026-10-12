package com.hbjt.fitness.agent.observability;

import com.hbjt.fitness.agent.observability.TraceContext.Span;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 把一次调用的全链路落库，并提供聚合查询。
 *
 * <p>三条硬性设计约束：
 *
 * <ol>
 *   <li><b>绝不拖慢主流程</b>：写入走独立单线程池异步执行，主线程不等。
 *       观测数据的价值在于"事后能查"，晚几毫秒写入毫无损失，
 *       但同步写会让每次对话多承担一次数据库往返。</li>
 *   <li><b>绝不让观测成为故障源</b>：任何写入异常都在池线程内部消化，
 *       连续失败到阈值后自动熔断（停止写入并关闭线程池）——
 *       数据库已经不健康了，再往里塞写请求只会雪上加霜。</li>
 *   <li><b>降级要可见</b>：熔断状态通过 {@code available} 字段暴露给前端，
 *       而不是静默丢弃数据让人误以为"今天没人用"。</li>
 * </ol>
 */
@Service
public class TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);

    /** 连续失败达到该次数后熔断，停止写入 */
    private static final int MAX_CONSECUTIVE_FAILURES = 20;

    /** 聚合时最多取多少条延迟样本，防止大表拖慢接口 */
    private static final int LATENCY_SAMPLE_LIMIT = 2000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ExecutorService pool;
    private final AtomicBoolean disabled = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public TraceRecorder(@Qualifier("chatJdbcTemplate") JdbcTemplate jdbc,
                         @Qualifier("chatTransactionTemplate") TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fitness-trace-recorder");
            t.setDaemon(true);
            return t;
        });
    }

    /** 观测是否可用（熔断后为 false）。 */
    public boolean isAvailable() {
        return !disabled.get();
    }

    /**
     * 记录一次调用。异步执行，不抛异常、不阻塞调用方。
     */
    public void record(TraceContext.Trace trace) {
        if (trace == null || trace.discard || disabled.get() || pool.isShutdown()) {
            return;
        }
        try {
            pool.submit(() -> write(trace));
        } catch (RejectedExecutionException ignore) {
            // 池已关闭：观测数据丢弃即可，不影响业务
        }
    }

    private void write(TraceContext.Trace trace) {
        LocalDateTime now = LocalDateTime.now();
        try {
            tx.execute(status -> {
                jdbc.update(
                        "INSERT INTO llm_trace (trace_id, session_key, owner_type, owner_id, intent,"
                                + " channel, model, prompt_version, status, error_type, degraded,"
                                + " latency_ms, prompt_tokens, completion_tokens, total_tokens,"
                                + " hit_count, top_score, user_text, created_at)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        trace.traceId(), trace.sessionKey, trace.ownerType, trace.ownerId,
                        trace.intent, trace.channel(), trace.model, trace.promptVersion,
                        trace.status, trace.errorType, trace.degraded ? 1 : 0,
                        trace.latencyMs(), trace.promptTokens, trace.completionTokens,
                        trace.totalTokens, trace.hitCount, trace.topScore,
                        clip(trace.userText, 500), now);

                List<Span> spans = trace.spans();
                for (Span s : spans) {
                    jdbc.update(
                            "INSERT INTO llm_span (trace_id, span_name, kind, start_ms, duration_ms,"
                                    + " detail, created_at) VALUES (?,?,?,?,?,?,?)",
                            trace.traceId(), s.name, s.kind, s.startOffsetMs,
                            Math.max(0, s.durationMs), s.detail, now);
                }
                return null;
            });
            consecutiveFailures.set(0);
        } catch (Exception e) {
            int n = consecutiveFailures.incrementAndGet();
            if (n == 1 || n % 20 == 0) {
                log.warn("链路记录写入失败（连续第 {} 次）：{}", n, e.getMessage());
            }
            if (n >= MAX_CONSECUTIVE_FAILURES && disabled.compareAndSet(false, true)) {
                log.error("链路记录连续失败 {} 次，已熔断停止写入；对话功能不受影响", n);
                pool.shutdown();
            }
        }
    }

    /**
     * 运行概览：调用量、失败率、降级率、Token、延迟分位。
     *
     * <p>延迟分位为什么不在 SQL 里算：MySQL 8 虽然有窗口函数，但按分位取值的写法
     * 既晦涩又依赖版本；而这里单日样本量在千级以内，取回内存排序反而更快更可靠，
     * 也便于统一 P50/P95/P99 的口径（向上取整取第 n 个）。
     */
    public Map<String, Object> overview(int days) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("available", isAvailable());
        if (!isAvailable()) {
            res.put("reason", "链路记录已熔断，数据库不可用");
            return res;
        }

        int span = Math.max(1, Math.min(days, 90));
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();

        try {
            long calls = count("SELECT COUNT(*) FROM llm_trace WHERE created_at >= ?", since);
            long errors = count("SELECT COUNT(*) FROM llm_trace WHERE created_at >= ? AND status='ERROR'", since);
            long degraded = count("SELECT COUNT(*) FROM llm_trace WHERE created_at >= ? AND degraded=1", since);
            Long tokens = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(total_tokens),0) FROM llm_trace WHERE created_at >= ?",
                    Long.class, since);
            Long hitSum = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(hit_count),0) FROM llm_trace WHERE created_at >= ? AND channel='knowledge'",
                    Long.class, since);
            Long kbCalls = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM llm_trace WHERE created_at >= ? AND channel='knowledge'",
                    Long.class, since);

            List<Integer> lat = jdbc.queryForList(
                    "SELECT latency_ms FROM llm_trace WHERE created_at >= ? ORDER BY latency_ms LIMIT "
                            + LATENCY_SAMPLE_LIMIT, Integer.class, since);

            res.put("days", span);
            res.put("calls", calls);
            res.put("errors", errors);
            res.put("degraded", degraded);
            res.put("errorRate", pct(errors, calls));
            res.put("degradedRate", pct(degraded, calls));
            res.put("tokens", tokens == null ? 0 : tokens);
            res.put("p50", percentile(lat, 50));
            res.put("p95", percentile(lat, 95));
            res.put("p99", percentile(lat, 99));
            res.put("avgLatency", lat.isEmpty() ? 0 : (long) lat.stream().mapToInt(Integer::intValue).average().orElse(0));

            double kbHitRate = (kbCalls == null || kbCalls == 0) ? 0.0
                    : (hitSum == null ? 0.0 : hitSum * 100.0 / kbCalls);
            res.put("knowledgeCalls", kbCalls == null ? 0 : kbCalls);
            res.put("knowledgeHitRate", Math.round(kbHitRate * 10) / 10.0);

            res.put("byIntent", groupCount(
                    "SELECT intent, COUNT(*) c FROM llm_trace WHERE created_at >= ? GROUP BY intent ORDER BY c DESC",
                    since));
            res.put("byStatus", groupCount(
                    "SELECT status, COUNT(*) c FROM llm_trace WHERE created_at >= ? GROUP BY status ORDER BY c DESC",
                    since));
            res.put("byDay", jdbc.queryForList(
                    "SELECT DATE(created_at) d, COUNT(*) c, SUM(CASE WHEN degraded=1 THEN 1 ELSE 0 END) g"
                            + " FROM llm_trace WHERE created_at >= ? GROUP BY DATE(created_at) ORDER BY d",
                    since));
        } catch (Exception e) {
            log.warn("运行概览查询失败：{}", e.getMessage());
            res.put("available", false);
            res.put("reason", e.getMessage());
        }
        return res;
    }

    /** 最近的调用流水（含各阶段耗时，供瀑布图使用）。 */
    public List<Map<String, Object>> traces(int limit, String intent, String status) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!isAvailable()) {
            return out;
        }
        int n = Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder(
                "SELECT trace_id, session_key, owner_type, owner_id, intent, channel, model,"
                        + " prompt_version, status, error_type, degraded, latency_ms, prompt_tokens,"
                        + " completion_tokens, total_tokens, hit_count, top_score, user_text, created_at"
                        + " FROM llm_trace");
        List<Object> args = new ArrayList<>();
        List<String> where = new ArrayList<>();
        if (intent != null && !intent.isBlank()) {
            where.add("intent = ?");
            args.add(intent);
        }
        if (status != null && !status.isBlank()) {
            where.add("status = ?");
            args.add(status);
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }
        sql.append(" ORDER BY id DESC LIMIT ").append(n);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            String tid = String.valueOf(row.get("trace_id"));
            List<Map<String, Object>> spans = jdbc.queryForList(
                    "SELECT span_name, kind, start_ms, duration_ms, detail FROM llm_span"
                            + " WHERE trace_id = ? ORDER BY start_ms, id", tid);
            row.put("spans", spans);
            out.add(row);
        }
        return out;
    }

    /** 刷新按天聚合指标，供仪表盘快速读取。 */
    public Map<String, Object> refreshDaily(int days) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (!isAvailable()) {
            res.put("available", false);
            return res;
        }
        int span = Math.max(1, Math.min(days, 90));
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int written = 0;

        try {
            for (int i = 0; i < span; i++) {
                LocalDate day = today.minusDays(i);
                LocalDateTime from = day.atStartOfDay();
                LocalDateTime to = day.plusDays(1).atStartOfDay();

                Long calls = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM llm_trace WHERE created_at >= ? AND created_at < ?",
                        Long.class, from, to);
                Long tokens = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(total_tokens),0) FROM llm_trace WHERE created_at >= ? AND created_at < ?",
                        Long.class, from, to);
                Long degraded = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM llm_trace WHERE created_at >= ? AND created_at < ? AND degraded=1",
                        Long.class, from, to);
                List<Integer> lat = jdbc.queryForList(
                        "SELECT latency_ms FROM llm_trace WHERE created_at >= ? AND created_at < ? ORDER BY latency_ms",
                        Integer.class, from, to);

                written += upsertDaily(day, "calls", calls == null ? 0 : calls, now);
                written += upsertDaily(day, "tokens", tokens == null ? 0 : tokens, now);
                written += upsertDaily(day, "degraded", degraded == null ? 0 : degraded, now);
                written += upsertDaily(day, "p95_latency", percentile(lat, 95), now);
            }
            res.put("available", true);
            res.put("days", span);
            res.put("metricsWritten", written);
        } catch (Exception e) {
            log.warn("日指标聚合失败：{}", e.getMessage());
            res.put("available", false);
            res.put("reason", e.getMessage());
        }
        return res;
    }

    private int upsertDaily(LocalDate day, String key, double value, LocalDateTime now) {
        return jdbc.update(
                "INSERT INTO agent_daily_metric (metric_date, metric_key, metric_value, updated_at)"
                        + " VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value),"
                        + " updated_at = VALUES(updated_at)",
                day, key, value, now);
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    private List<Map<String, Object>> groupCount(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0.0 : Math.round(part * 1000.0 / total) / 10.0;
    }

    /** 分位数：向上取整取第 n 小的值，空样本返回 0 */
    static long percentile(List<Integer> values, int p) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int rank = (int) Math.ceil(sorted.size() * p / 100.0) - 1;
        rank = Math.max(0, Math.min(rank, sorted.size() - 1));
        return sorted.get(rank);
    }

    static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
