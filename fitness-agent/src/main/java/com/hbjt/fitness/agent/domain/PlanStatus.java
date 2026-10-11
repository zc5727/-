package com.hbjt.fitness.agent.domain;

/**
 * 结果状态。
 * <ul>
 *   <li>{@link #OK} 计划由 LLM 生成，且通过了结构完整性校验</li>
 *   <li>{@link #DEGRADED} LLM 不可用 / 输出不合格，已回退到规则引擎，结果依然可用</li>
 * </ul>
 * 刻意不设 FAILED：本智能体的契约是「永不返回空结果」。
 */
public enum PlanStatus {
    OK,
    DEGRADED
}
