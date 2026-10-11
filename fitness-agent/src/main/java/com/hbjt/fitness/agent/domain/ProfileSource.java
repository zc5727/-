package com.hbjt.fitness.agent.domain;

/**
 * 档案来源，用于前端提示与日志追溯。
 */
public enum ProfileSource {
    /** 请求直接录入 */
    INLINE("直接录入"),
    /** 从业务系统会员档案读取 */
    MEMBER_RECORD("会员档案");

    private final String label;

    ProfileSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
