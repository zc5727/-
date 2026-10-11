package com.gym.entity;

import lombok.Data;

@Data
public class Admin {
    private Integer adminNo;
    private String adminAccount;
    private String adminPassword;
    private String token;
}
