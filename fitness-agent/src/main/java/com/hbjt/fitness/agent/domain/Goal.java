package com.hbjt.fitness.agent.domain;

/**
 * 健身目标。决定热量盈亏与蛋白质系数。
 */
public enum Goal {
    LOSE_FAT("减脂"),
    GAIN_MUSCLE("增肌"),
    SHAPE("塑形"),
    MAINTAIN("维持");

    private final String label;

    Goal(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
