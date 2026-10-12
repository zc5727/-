package com.gym.entity;

import lombok.Data;

import java.util.Date;

/**
 * 流失预警信号。
 *
 * <p>一条信号只描述一件事（比如"连续 45 天未到店"），带自己的权重。
 * 这样做的好处是预警可解释：管理员看到的不是"该会员流失概率 73%"
 * 这种无从验证的数字，而是三条具体的、可以逐条核实的理由。
 */
@Data
public class MemberChurnSignal {

    private Long id;
    private Integer memberNo;

    /** NO_CHECKIN 久未到店 / LOW_BALANCE 余额偏低 / NO_RECHARGE 长期未充值 */
    private String signalType;

    /** 权重，越大越危险 */
    private Double weight;

    private String detail;
    private Date detectedAt;
}
