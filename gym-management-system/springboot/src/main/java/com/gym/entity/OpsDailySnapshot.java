package com.gym.entity;

import lombok.Data;

import java.util.Date;

/**
 * 运营日快照。
 *
 * <p>把当天的新增会员、签到人次、充值金额、课程上座率固化成一行。
 * 有了它，看趋势就是读一张小表，不需要每次都去扫签到和充值流水——
 * 后两者会随数据量增长而变慢，快照表的规模只和天数有关。
 */
@Data
public class OpsDailySnapshot {

    private Long id;
    private Date snapshotDate;
    private Integer newMembers;
    private Integer checkinCount;
    private Double rechargeAmount;
    private Double courseSeatRate;
    private Date createdAt;
}
