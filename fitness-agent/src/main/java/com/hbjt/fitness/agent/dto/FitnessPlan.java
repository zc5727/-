package com.hbjt.fitness.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * 健身计划（LLM 生成或规则生成的统一结构）。
 */
public record FitnessPlan(
        String summary,
        List<String> training,
        List<String> diet,
        List<String> precautions
) {
    /**
     * 结构完整性判定：三个维度至少要有一个有内容，否则视为无效计划。
     * 标 @JsonIgnore，避免被序列化成响应里的 "empty" 冗余字段。
     */
    @JsonIgnore
    public boolean isEmpty() {
        return (summary == null || summary.isBlank())
                && (training == null || training.isEmpty())
                && (diet == null || diet.isEmpty())
                && (precautions == null || precautions.isEmpty());
    }
}
