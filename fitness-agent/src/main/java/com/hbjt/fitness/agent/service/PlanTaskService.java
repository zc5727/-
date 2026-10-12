package com.hbjt.fitness.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbjt.fitness.agent.dto.FitnessPlan;
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
import java.util.Locale;
import java.util.Map;

/**
 * 计划执行闭环：把一份计划拆成每日任务，支持打卡，并据此计算依从率。
 *
 * <p>为什么需要它：生成计划只完成了"说"的部分。健身这件事真正难的是"做"，
 * 而系统此前对"做了没有"一无所知。有了任务与打卡之后，
 * 才能回答"这个会员这周练了几天"，也才能在依从率过低时主动降低下次计划的强度——
 * 一个总也完不成的计划，本身就是设计失败。
 *
 * <p>节律说明：采用「练二休一」的固定节律生成一周 7 天。
 * 这不是训练科学上的最优解，而是可解释、可预期：用户能一眼看懂为什么今天休息，
 * 而不是面对一个黑箱排出来的日程。
 */
@Service
public class PlanTaskService {

    private static final Logger log = LoggerFactory.getLogger(PlanTaskService.class);

    /** 一周内的训练日上限，保证至少有一天完全休息 */
    private static final int MAX_TRAIN_DAYS = 5;

    /** 训练内容为空时的默认训练日数量 */
    private static final int DEFAULT_TRAIN_DAYS = 3;

    /** 一周七天的节律模板：rest 表示休息日 */
    private static final String[] RHYTHM = {
            "cardio", "strength", "rest", "strength", "cardio", "rest", "rest"
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper om;

    public PlanTaskService(@Qualifier("chatJdbcTemplate") JdbcTemplate jdbc,
                           @Qualifier("chatTransactionTemplate") TransactionTemplate tx,
                           ObjectMapper om) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.om = om;
    }

    /** 依从率低于该比例时，下次生成计划自动降一档强度 */
    private static final double LOW_ADHERENCE_THRESHOLD = 0.5;

    /**
     * 把一份计划展开为一周的每日任务。
     *
     * <p>幂等：以 {@code (plan_key, day_index)} 为唯一键，重复调用（如会话重放）
     * 只会覆盖任务内容，不会产生重复行。
     *
     * @return 生成的任务条数；存储不可用时返回 0
     */
    public int materialize(String planKey, String sessionKey, String ownerType, String ownerId,
                           FitnessPlan plan, LocalDate startDate) {
        if (plan == null || planKey == null || planKey.isBlank()) {
            return 0;
        }
        List<String> training = plan.training() == null ? List.of() : plan.training();
        int trainDays = training.isEmpty()
                ? DEFAULT_TRAIN_DAYS
                : Math.min(training.size(), MAX_TRAIN_DAYS);

        LocalDate base = startDate == null ? LocalDate.now() : startDate;
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> batch = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < RHYTHM.length; i++) {
            boolean isTrainingDay = !"rest".equals(RHYTHM[i]) && used < trainDays;
            int dayIndex = i + 1;
            LocalDate date = base.plusDays(i);

            String type;
            String title;
            String detail;
            if (isTrainingDay) {
                String item = training.isEmpty()
                        ? "自主训练"
                        : training.get(used % training.size());
                type = classify(item);
                title = clip(item.trim(), 200);
                detail = json(Map.of("source", "plan", "index", used));
                used++;
            } else {
                type = "rest";
                title = "休息日：轻度活动与拉伸";
                detail = json(Map.of("source", "plan", "rest", true));
            }

            batch.add(new Object[]{
                    planKey, sessionKey, ownerType, ownerId, dayIndex,
                    type, title, detail, date, "TODO", now
            });
        }

        try {
            int[] rows = tx.execute(status -> {
                int[] r = new int[batch.size()];
                int k = 0;
                for (Object[] p : batch) {
                    r[k++] = jdbc.update(
                            "INSERT INTO plan_task (plan_key, session_key, owner_type, owner_id,"
                                    + " day_index, task_type, title, detail_json, planned_date, status, created_at)"
                                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?)"
                                    + " ON DUPLICATE KEY UPDATE title=VALUES(title),"
                                    + " task_type=VALUES(task_type), detail_json=VALUES(detail_json),"
                                    + " planned_date=VALUES(planned_date)",
                            p);
                }
                return r;
            });
            int n = rows == null ? 0 : rows.length;
            log.info("计划任务展开 planKey={} 训练日={} 条数={}", planKey, trainDays, n);
            return n;
        } catch (Exception e) {
            log.warn("计划任务展开失败（不影响计划生成）：{}", e.getMessage());
            return 0;
        }
    }

    /** 查询某归属在日期区间内的任务。 */
    public List<Map<String, Object>> list(String ownerType, String ownerId,
                                          LocalDate from, LocalDate to) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            out = jdbc.queryForList(
                    "SELECT id, plan_key, session_key, day_index, task_type, title,"
                            + " detail_json, planned_date, status, created_at"
                            + " FROM plan_task WHERE owner_type=? AND owner_id=?"
                            + " AND planned_date >= ? AND planned_date <= ?"
                            + " ORDER BY planned_date, day_index",
                    ownerType, ownerId, from, to);
        } catch (Exception e) {
            log.warn("任务查询失败：{}", e.getMessage());
        }
        return out;
    }

    /**
     * 打卡（完成 / 跳过 / 撤销）。
     *
     * <p>幂等：先按 task_id 定位，再写流水并更新任务状态。重复提交同一动作时，
     * 流水会多一条（用于审计"点了几次"），但任务最终状态是确定的。
     * 越权防护：更新条件里带 owner_type / owner_id，改不到别人的任务。
     *
     * @return 受影响行数；0 表示任务不存在或不属于该用户
     */
    public int checkIn(long taskId, String ownerType, String ownerId,
                       String action, String actualValue, String note) {
        String act = action == null ? "DONE" : action.trim().toUpperCase(Locale.ROOT);
        String next = switch (act) {
            case "DONE" -> "DONE";
            case "SKIP" -> "SKIPPED";
            case "UNDO" -> "TODO";
            default -> "DONE";
        };
        try {
            return tx.execute(status -> {
                jdbc.update(
                        "INSERT INTO plan_task_log (task_id, owner_type, owner_id, action,"
                                + " actual_value, note, created_at) VALUES (?,?,?,?,?,?,?)",
                        taskId, ownerType, ownerId, act, clip(actualValue, 200), clip(note, 500),
                        LocalDateTime.now());
                return jdbc.update(
                        "UPDATE plan_task SET status=? WHERE id=? AND owner_type=? AND owner_id=?",
                        next, taskId, ownerType, ownerId);
            });
        } catch (Exception e) {
            log.warn("打卡写入失败：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 依从率统计。
     *
     * <p>口径：已完成 / 已完成 + 跳过 + 过期未完成。
     * 「过期未完成」指计划日期已过但状态仍是 TODO 的任务——
     * 它才是真正反映"没跟上"的部分，不算进去的话依从率会虚高。
     */
    public Map<String, Object> adherence(String ownerType, String ownerId, int days) {
        Map<String, Object> res = new LinkedHashMap<>();
        int span = Math.max(1, Math.min(days, 90));
        LocalDate from = LocalDate.now().minusDays(span - 1L);
        try {
            Integer done = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM plan_task WHERE owner_type=? AND owner_id=?"
                            + " AND planned_date >= ? AND status='DONE'",
                    Integer.class, ownerType, ownerId, from);
            Integer skipped = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM plan_task WHERE owner_type=? AND owner_id=?"
                            + " AND planned_date >= ? AND status='SKIPPED'",
                    Integer.class, ownerType, ownerId, from);
            Integer pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM plan_task WHERE owner_type=? AND owner_id=?"
                            + " AND planned_date >= ? AND status='TODO' AND planned_date < CURDATE()",
                    Integer.class, ownerType, ownerId, from);

            int d = done == null ? 0 : done;
            int s = skipped == null ? 0 : skipped;
            int p = pending == null ? 0 : pending;
            int total = d + s + p;

            res.put("available", true);
            res.put("days", span);
            res.put("done", d);
            res.put("skipped", s);
            res.put("overdue", p);
            res.put("rate", total == 0 ? 0.0 : Math.round(d * 1000.0 / total) / 10.0);
            res.put("low", total > 0 && (double) d / total < LOW_ADHERENCE_THRESHOLD);
        } catch (Exception e) {
            log.warn("依从率统计失败：{}", e.getMessage());
            res.put("available", false);
            res.put("reason", e.getMessage());
        }
        return res;
    }

    /** 关键词判定训练类型；无法判定时按力量处理（力量动作更需要指导）。 */
    static String classify(String item) {
        String s = item == null ? "" : item;
        if (s.contains("跑") || s.contains("走") || s.contains("骑") || s.contains("游泳")
                || s.contains("有氧") || s.contains("心肺") || s.contains("跳")
                || s.contains("跳绳") || s.contains("椭圆")) {
            return "cardio";
        }
        return "strength";
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String json(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
