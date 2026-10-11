package com.hbjt.fitness.agent.dto;

/**
 * 对话回合的状态。
 * <ul>
 *   <li>{@code ASK} —— 信息还不齐，Agent 在追问，本轮没有计划</li>
 *   <li>{@code OK} —— 信息齐备且由大模型生成计划</li>
 *   <li>{@code DEGRADED} —— 信息齐备但大模型不可用，已由规则引擎生成等效计划</li>
 * </ul>
 * 与 {@link com.hbjt.fitness.agent.domain.PlanStatus} 的区别：多了一个「追问」态。
 */
public enum ChatStatus {
    ASK,
    OK,
    DEGRADED
}
