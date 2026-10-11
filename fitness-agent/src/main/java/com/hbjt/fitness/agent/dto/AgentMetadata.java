package com.hbjt.fitness.agent.dto;

import java.util.List;

/**
 * 能力元数据。前端据此渲染下拉选项与范围提示，避免前后端枚举硬编码不一致。
 */
public record AgentMetadata(
        List<Option> genders,
        List<Option> goals,
        List<Option> activityLevels,
        List<Option> models,
        Range age,
        Range height,
        Range weight,
        Range bodyFat,
        boolean llmConfigured,
        String llmModel,
        String llmVendor
) {
    public record Option(String value, String label, String description) {
    }

    public record Range(double min, double max, String unit) {
    }
}
