package com.hbjt.fitness.agent.domain;

/**
 * 性别。参与 BMR（Mifflin-St Jeor）与体脂分级的计算。
 */
public enum Gender {
    MALE("男"),
    FEMALE("女");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 兼容业务系统 member 表里的 "M" / "F" 单字符编码。
     */
    public static Gender fromGymCode(String code) {
        if (code == null) {
            return null;
        }
        return switch (code.trim().toUpperCase()) {
            case "M", "MALE", "1", "男" -> MALE;
            case "F", "FEMALE", "0", "2", "女" -> FEMALE;
            default -> null;
        };
    }
}
