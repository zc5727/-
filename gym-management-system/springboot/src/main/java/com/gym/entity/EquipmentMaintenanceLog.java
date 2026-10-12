package com.gym.entity;

import lombok.Data;

import java.util.Date;

/**
 * 器材工单流转日志。
 *
 * <p>记录每一次状态变更（谁、什么时候、做了什么、备注）。
 * 工单表只保留"当前状态"，历史全部留在这里，两者分开是为了
 * 让"当前态"查询足够轻，同时不丢审计线索。
 */
@Data
public class EquipmentMaintenanceLog {

    private Long id;
    private Long orderId;

    /** CREATE 建单 / ASSIGN 派工 / FINISH 完成 / VERIFY 验收 */
    private String action;

    private String operator;
    private String note;
    private Date createdAt;
}
