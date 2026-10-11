package com.hbjt.fitness.agent.domain;

/**
 * 数值来源。证据驱动的关键：每个结论都要能说清"这个数是怎么来的"。
 * <p>业务系统的 member 表没有体脂率字段，因此体脂率可能是估算值，
 * 必须与实测值区分开，避免把推算结果当成实测数据使用。
 */
public enum ValueSource {
    /** 用户/档案中直接给出 */
    MEASURED("实测"),
    /** 由 BMI + 年龄 + 性别推算（Deurenberg 公式） */
    ESTIMATED("估算"),
    /** 使用了兜底默认值 */
    DEFAULTED("默认值");

    private final String label;

    ValueSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
