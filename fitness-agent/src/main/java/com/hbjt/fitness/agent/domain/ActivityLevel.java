package com.hbjt.fitness.agent.domain;

/**
 * 活动水平 → TDEE 活动系数。
 * <p>原实现把 1.55 写死在代码里；这里改为可传入、默认 MODERATE，
 * 会员可被更准确地评估，也保留了原行为作为默认值。
 */
public enum ActivityLevel {
    SEDENTARY("久坐（几乎不运动）", 1.20),
    LIGHT("轻度（每周 1-3 次）", 1.375),
    MODERATE("中度（每周 3-5 次）", 1.55),
    ACTIVE("高度（每周 6-7 次）", 1.725),
    VERY_ACTIVE("极高（体力工作/一日两练）", 1.90);

    private final String label;
    private final double factor;

    ActivityLevel(String label, double factor) {
        this.label = label;
        this.factor = factor;
    }

    public String label() {
        return label;
    }

    public double factor() {
        return factor;
    }
}
