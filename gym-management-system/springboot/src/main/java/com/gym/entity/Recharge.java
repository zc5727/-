package com.gym.entity;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Recharge {
    private Integer rechargeNo;
    private LocalDate rechargeDate;
    private String rechargeMethod;
    private Integer rechargeStatus;
    private Double rechargeMoney;
    private Integer memberNo;
}
