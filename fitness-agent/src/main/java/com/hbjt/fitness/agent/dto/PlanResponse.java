package com.hbjt.fitness.agent.dto;

import com.hbjt.fitness.agent.domain.PlanStatus;

/**
 * 智能问答响应。契约要点：{@code status} 只有 OK / DEGRADED 两种，
 * 任何情况下都会带回一份可用的 {@code plan}，绝不返回空。
 */
public record PlanResponse(
        PlanStatus status,
        HealthMetrics metrics,
        FitnessPlan plan,
        /** 面向调用方的可读说明：由谁生成、降级原因 */
        String message,
        /** 降级时的原因分类，便于前端做差异化提示 */
        String degradedReason,
        /** 生成耗时 ms，便于观测 */
        long elapsedMs,
        /** 生效的提示词版本；降级时为 null。线上出现输出质量波动时据此归因 */
        String promptVersion
) {
    public static PlanResponse ok(HealthMetrics m, FitnessPlan p, long ms, String promptVersion) {
        return new PlanResponse(PlanStatus.OK, m, p,
                "由大模型生成（提示词 " + promptVersion + "）", null, ms, promptVersion);
    }

    public static PlanResponse degraded(HealthMetrics m, FitnessPlan p, String reason, long ms) {
        return new PlanResponse(PlanStatus.DEGRADED, m, p,
                "大模型暂不可用，已由规则引擎生成等效计划（" + reason + "）", reason, ms, null);
    }
}
