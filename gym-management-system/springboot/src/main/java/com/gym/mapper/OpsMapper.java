package com.gym.mapper;

import com.gym.entity.EquipmentMaintenance;
import com.gym.entity.EquipmentMaintenanceLog;
import com.gym.entity.MemberChurnSignal;
import com.gym.entity.MemberSegment;
import com.gym.entity.OpsDailySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 运营中枢的数据访问。
 *
 * <p>所有写操作都走「带条件的 UPDATE / INSERT IGNORE」，
 * 保证重复执行（定时任务重跑、接口重试）不会重复计数或重复入账。
 */
@Mapper
@Repository
public interface OpsMapper {

    /* ==================== 会员分层 ==================== */

    /**
     * 一次性算出全体会员的 RFM 原始值。
     *
     * <p>用 LEFT JOIN + IFNULL 而不是先查会员再逐个查，
     * 是为了避免 N+1：几千会员逐个查会把一次统计拖成几千次往返。
     */
    List<Map<String, Object>> rfmRaw(@Param("days") int days);

    /** 写入或更新某会员的分层结果。 */
    int upsertSegment(MemberSegment seg);

    /** 按层取会员；segment 为空则取全部。 */
    List<Map<String, Object>> listSegment(@Param("segment") String segment);

    /** 全部分层结果，供流失信号重算使用。 */
    List<MemberSegment> listAllSegments();

    /** 各层人数分布。 */
    List<Map<String, Object>> countBySegment();

    /* ==================== 流失预警信号 ==================== */

    /** 删除某批之前（含）的信号，用于整批重算。 */
    int deleteSignalsBefore(@Param("time") Date time);

    int insertSignal(MemberChurnSignal sig);

    /** 风险排行：按信号总权重降序。 */
    List<Map<String, Object>> churnRanking(@Param("limit") int limit);

    /* ==================== 运营日快照 ==================== */

    int upsertSnapshot(OpsDailySnapshot snap);

    List<Map<String, Object>> recentSnapshots(@Param("days") int days);

    /* ==================== 器材维护工单 ==================== */

    int insertOrder(EquipmentMaintenance order);

    /**
     * 推进工单状态。
     *
     * <p>{@code from} 里带上当前允许的前置状态，因此重复提交时影响行数为 0，
     * 天然幂等；验收时同时写入 cost 与 finished_at。
     */
    int advanceOrder(@Param("id") long id,
                     @Param("from") String from,
                     @Param("to") String to,
                     @Param("assignee") String assignee,
                     @Param("cost") Double cost,
                     @Param("finishedAt") Date finishedAt);

    List<Map<String, Object>> listOrders(@Param("status") String status);

    int insertOrderLog(EquipmentMaintenanceLog log);

    List<Map<String, Object>> orderLogs(@Param("orderId") long orderId);

    /* ==================== 运营日快照的原子指标 ==================== */

    /** 当日新增会员数（以开卡时间 cardTime 为准） */
    Integer countNewMembers(@Param("d") Date d);

    /** 当日签到人次 */
    Integer countCheckins(@Param("d") Date d);

    /** 当日已支付充值金额 */
    Double sumRecharge(@Param("d") Date d);

    /** 课程总数 */
    Integer countCourses();

    /** 报名总人次 */
    Long countRegisters();
}
