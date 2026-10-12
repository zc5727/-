package com.gym.entity;

import lombok.Data;

import java.util.Date;

/**
 * 会员分层（RFM）。
 *
 * <p>R（recency）距最近一次到店天数、F（frequency）近 90 天到店次数、
 * M（monetary）近 90 天充值金额。三个维度各自打分后加权得到 {@code score}，
 * 再按 score 与 R 的阈值划入四层：
 *
 * <ul>
 *   <li>{@code HIGH_VALUE} 高价值：常来且持续充值</li>
 *   <li>{@code GROWING} 成长：近期活跃但贡献尚低</li>
 *   <li>{@code SLEEPING} 沉睡：曾经活跃但久未到店</li>
 *   <li>{@code CHURN_RISK} 流失风险：久未到店且有余额未消耗</li>
 * </ul>
 *
 * <p>存结果而不是每次现算，是为了让"分层随时间怎么变"这件事可查——
 * 这也是后面做环比与预警的前提。
 */
@Data
public class MemberSegment {

    private Long id;
    private Integer memberNo;
    private Integer recencyDays;
    private Integer frequency;
    private Double monetary;
    private String segment;
    private Double score;
    private Date computedAt;
}
