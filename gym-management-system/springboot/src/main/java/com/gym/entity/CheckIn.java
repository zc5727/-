package com.gym.entity;

import lombok.Data;

import java.time.LocalDate;

/*签到表*/

@Data
public class CheckIn {
    private Integer checkNo;
    private Integer memberNo;
    private LocalDate checkDate;
}
