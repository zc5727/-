package com.gym.entity;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Register {
    private Integer registerNo;
    private Integer courseNo;
    private Integer memberNo;
    private String courseName;
    private LocalDate courseTime;
    private Integer courseDuration;
    private Integer employeeNo;
    private String employeeName;
    private String employeePhone;
    private String memberName;
    private String memberPhone;
}
