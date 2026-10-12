package com.gym.entity;

import lombok.Data;

import java.util.Date;

/**
 * 器材维护工单。
 *
 * <p>把"器材坏了"从一句口头反馈变成有状态、有负责人、有成本的闭环：
 * PLANNED（待派工）→ ASSIGNED（已派工）→ DONE（已完成）→ VERIFIED（已验收）。
 *
 * <p>状态推进用带条件的 UPDATE 实现（只允许从合法的前置状态推进），
 * 因此重复提交不会造成状态跳变，也不会重复计成本。
 */
@Data
public class EquipmentMaintenance {

    private Long id;
    private Integer equipmentNo;

    /** ROUTINE 例行保养 / REPAIR 故障维修 */
    private String planType;

    private String title;

    /** PLANNED 待派工 / ASSIGNED 已派工 / DONE 已完成 / VERIFIED 已验收 */
    private String status;

    private String assignee;
    private Double cost;
    private Date plannedDate;
    private Date finishedAt;
    private Date createdAt;
}
