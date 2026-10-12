package com.gym.service;

import com.gym.entity.EquipmentMaintenance;
import com.gym.entity.EquipmentMaintenanceLog;
import com.gym.entity.MemberChurnSignal;
import com.gym.entity.MemberSegment;
import com.gym.entity.OpsDailySnapshot;
import com.gym.mapper.OpsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员智能运营中枢。
 *
 * <p>补齐原系统只有 CRUD、没有"经营判断"的缺口。三个能力：
 * <ol>
 *   <li><b>会员分层</b>：按 RFM 把会员划入高价值 / 成长 / 沉睡 / 流失风险四层，
 *       让"谁该重点维护"从拍脑袋变成可排序的清单。</li>
 *   <li><b>流失预警</b>：输出的是具体信号（多少天没来、多久没充值），
 *       而不是一个无从验证的"流失概率"——后者无法核实，也就无法据此行动。</li>
 *   <li><b>运营日快照</b>：把每天的关键指标固化下来，趋势查询不必扫全量流水。</li>
 * </ol>
 *
 * <p>所有计算都是<b>只读原始表 + 结果落库</b>。会员、签到、充值等原始表保持不动，
 * 既有业务逻辑零改动；算错了删掉重跑即可。
 */
@Service
public class OpsIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(OpsIntelligenceService.class);

    /** 分层阈值：最近到店天数 */
    private static final int R_HIGH_VALUE = 14;
    private static final int R_GROWING = 30;
    private static final int R_SLEEPING = 60;

    /** 高价值层还要求近 90 天到店不少于该次数 */
    private static final int F_HIGH_VALUE = 8;

    /** 从未签到时，按该天数参与打分（比最久未到店更差） */
    private static final int R_NEVER = 365;

    @Autowired
    private OpsMapper opsMapper;

    /**
     * 一次性重算：分层 → 流失信号 → 当日快照。
     *
     * @return 各步骤写入条数
     */
    public Map<String, Object> refresh(int days) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            int seg = recomputeSegments(days);
            int sig = recomputeChurn();
            int snap = buildSnapshot(LocalDate.now());
            res.put("available", true);
            res.put("segments", seg);
            res.put("signals", sig);
            res.put("snapshots", snap);
        } catch (Exception e) {
            log.warn("运营指标重算失败：{}", e.getMessage());
            res.put("available", false);
            res.put("reason", e.getMessage());
        }
        return res;
    }

    /** 运营概览：分层分布 + 风险榜 + 近期快照。 */
    public Map<String, Object> overview(int days) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            res.put("available", true);
            res.put("segmentCounts", opsMapper.countBySegment());
            res.put("churnRanking", opsMapper.churnRanking(20));
            res.put("snapshots", opsMapper.recentSnapshots(days));
        } catch (Exception e) {
            log.warn("运营概览查询失败：{}", e.getMessage());
            res.put("available", false);
            res.put("reason", e.getMessage());
        }
        return res;
    }

    /** 重算会员分层。 */
    public int recomputeSegments(int days) {
        List<Map<String, Object>> raw = opsMapper.rfmRaw(days);
        Date now = new Date();
        int n = 0;
        for (Map<String, Object> r : raw) {
            Integer memberNo = toInt(r.get("memberNo"));
            if (memberNo == null) {
                continue;
            }
            Integer recency = toInt(r.get("recencyDays"));
            Integer freqRaw = toInt(r.get("frequency"));
            Double moneyRaw = toDouble(r.get("monetary"));
            int freq = freqRaw == null ? 0 : freqRaw;
            double money = moneyRaw == null ? 0.0 : moneyRaw;

            MemberSegment seg = new MemberSegment();
            seg.setMemberNo(memberNo);
            seg.setRecencyDays(recency);
            seg.setFrequency(freq);
            seg.setMonetary(money);
            seg.setSegment(classify(recency, freq, money));
            seg.setScore(round1(score(recency, freq, money)));
            seg.setComputedAt(now);
            n += opsMapper.upsertSegment(seg);
        }
        log.info("会员分层重算完成：会员 {} 人，写入 {} 行", raw.size(), n);
        return n;
    }

    /**
     * 分层判定。
     *
     * <p>{@code r == null} 表示从未签到，归入最差一档；
     * 若此时近 90 天还有充值（充了钱却没来过），判定为流失风险而不是沉睡——
     * 这两种情况该采取的动作不同：前者需要主动召回，后者属于长期未激活。
     */
    static String classify(Integer r, int f, double m) {
        if (r == null) {
            return m > 0 ? "CHURN_RISK" : "SLEEPING";
        }
        if (r <= R_HIGH_VALUE && f >= F_HIGH_VALUE) {
            return "HIGH_VALUE";
        }
        if (r <= R_GROWING) {
            return "GROWING";
        }
        if (r <= R_SLEEPING) {
            return "SLEEPING";
        }
        return m > 0 ? "CHURN_RISK" : "SLEEPING";
    }

    /** 综合得分：R 权重最高（活跃度最能代表当下状态），F 次之，M 最低。 */
    static double score(Integer r, int f, double m) {
        double rVal = r == null ? R_NEVER : r;
        double rScore = Math.max(0.0, 100.0 - rVal * 1.2);
        double fScore = Math.min(100.0, f * 8.0);
        double mScore = Math.min(100.0, m / 5.0);
        return 0.40 * rScore + 0.35 * fScore + 0.25 * mScore;
    }

    /**
     * 重算流失预警信号。
     *
     * <p>先整批删除再重建，而不是逐条 diff：会员量在千级规模时，
     * 全量重算一次只要几秒，换来的是"表里一定和当前数据一致"这个确定性。
     */
    public int recomputeChurn() {
        Date now = new Date();
        opsMapper.deleteSignalsBefore(now);

        List<MemberSegment> segs = opsMapper.listAllSegments();
        int n = 0;
        for (MemberSegment s : segs) {
            Integer r = s.getRecencyDays();
            Double md = s.getMonetary();
            double money = md == null ? 0.0 : md;

            if (r == null) {
                n += addSignal(s.getMemberNo(), "NO_CHECKIN", 50.0, "从未到店签到");
            } else if (r > R_SLEEPING) {
                n += addSignal(s.getMemberNo(), "NO_CHECKIN", 40.0, "已 " + r + " 天未到店");
            } else if (r > R_GROWING) {
                n += addSignal(s.getMemberNo(), "NO_CHECKIN", 25.0, "已 " + r + " 天未到店");
            }
            if (money <= 0) {
                n += addSignal(s.getMemberNo(), "NO_RECHARGE", 20.0, "近 90 天无有效充值");
            }
        }
        log.info("流失预警信号重算完成：{} 条", n);
        return n;
    }

    private int addSignal(Integer memberNo, String type, double weight, String detail) {
        MemberChurnSignal s = new MemberChurnSignal();
        s.setMemberNo(memberNo);
        s.setSignalType(type);
        s.setWeight(weight);
        s.setDetail(detail);
        s.setDetectedAt(new Date());
        return opsMapper.insertSignal(s);
    }

    /** 分层名单；segment 为空返回全部。 */
    public List<Map<String, Object>> listSegments(String segment) {
        try {
            return opsMapper.listSegment(segment);
        } catch (Exception e) {
            log.warn("分层名单查询失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /* ==================== 器材维护工单 ==================== */

    /** 建单。 */
    public Map<String, Object> createOrder(int equipmentNo, String planType, String title,
                                           String plannedDate, String operator) {
        EquipmentMaintenance o = new EquipmentMaintenance();
        o.setEquipmentNo(equipmentNo);
        o.setPlanType(planType == null || planType.trim().isEmpty() ? "ROUTINE" : planType.trim().toUpperCase());
        o.setTitle(title == null || title.trim().isEmpty() ? "器材维护" : title.trim());
        o.setStatus("PLANNED");
        o.setPlannedDate(parseDate(plannedDate));
        o.setCreatedAt(new Date());

        Map<String, Object> res = new LinkedHashMap<>();
        try {
            opsMapper.insertOrder(o);
            writeLog(o.getId(), "CREATE", operator, "工单创建");
            res.put("ok", true);
            res.put("orderId", o.getId());
        } catch (Exception e) {
            log.warn("器材工单创建失败：{}", e.getMessage());
            res.put("ok", false);
            res.put("reason", e.getMessage());
        }
        return res;
    }

    /**
     * 推进工单状态。
     *
     * <p>合法路径：PLANNED→ASSIGNED→DONE→VERIFIED。
     * 用带前置状态的 UPDATE，跨步或重复提交影响行数为 0。
     */
    public Map<String, Object> advanceOrder(long id, String to, String assignee,
                                            Double cost, String operator) {
        String target = (to == null ? "" : to.trim().toUpperCase());

        // 合法路径：PLANNED→ASSIGNED→DONE→VERIFIED。
        // 这里写成 if/else 而不是 Map.of，是因为后端跑在 Java 8 上，
        // Map.of 与 switch 表达式（Java 9+/14+）都用不了。
        String from;
        if ("ASSIGNED".equals(target)) {
            from = "PLANNED";
        } else if ("DONE".equals(target)) {
            from = "ASSIGNED";
        } else if ("VERIFIED".equals(target)) {
            from = "DONE";
        } else {
            from = null;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        if (from == null) {
            res.put("ok", false);
            res.put("reason", "不支持的目标状态：" + target);
            return res;
        }
        try {
            Date finishedAt = "DONE".equals(target) ? new Date() : null;
            int n = opsMapper.advanceOrder(id, from, target, assignee, cost, finishedAt);
            if (n > 0) {
                writeLog(id, target, operator, null);
            }
            res.put("ok", n > 0);
            res.put("affected", n);
        } catch (Exception e) {
            log.warn("器材工单推进失败：{}", e.getMessage());
            res.put("ok", false);
            res.put("reason", e.getMessage());
        }
        return res;
    }

    public List<Map<String, Object>> listOrders(String status) {
        try {
            return opsMapper.listOrders(status);
        } catch (Exception e) {
            log.warn("器材工单查询失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> orderLogs(long orderId) {
        try {
            return opsMapper.orderLogs(orderId);
        } catch (Exception e) {
            log.warn("工单流转日志查询失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 生成当日运营快照。 */
    public int buildSnapshot(LocalDate date) {
        try {
            Date d = java.sql.Date.valueOf(date);
            int news = nz(opsMapper.countNewMembers(d));
            int checks = nz(opsMapper.countCheckins(d));
            Double sum = opsMapper.sumRecharge(d);
            double amount = sum == null ? 0.0 : sum;

            OpsDailySnapshot snap = new OpsDailySnapshot();
            snap.setSnapshotDate(d);
            snap.setNewMembers(news);
            snap.setCheckinCount(checks);
            snap.setRechargeAmount(amount);
            snap.setCourseSeatRate(seatRate());
            snap.setCreatedAt(new Date());
            return opsMapper.upsertSnapshot(snap);
        } catch (Exception e) {
            log.warn("运营日快照生成失败：{}", e.getMessage());
            return 0;
        }
    }

    /** 课程平均报名人次；无课程时返回 null（而不是 0，避免把"没有课程"显示成"零上座"）。 */
    private Double seatRate() {
        Integer courses = opsMapper.countCourses();
        if (courses == null || courses <= 0) {
            return null;
        }
        Long regs = opsMapper.countRegisters();
        long r = regs == null ? 0L : regs;
        return Math.round(r * 100.0 / courses) / 100.0;
    }

    /** 记一条流转日志。 */
    private void writeLog(long orderId, String action, String operator, String note) {
        EquipmentMaintenanceLog l = new EquipmentMaintenanceLog();
        l.setOrderId(orderId);
        l.setAction(action);
        l.setOperator(operator);
        l.setNote(note);
        l.setCreatedAt(new Date());
        opsMapper.insertOrderLog(l);
    }

    /* ==================== 工具 ==================== */

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static java.sql.Date parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
