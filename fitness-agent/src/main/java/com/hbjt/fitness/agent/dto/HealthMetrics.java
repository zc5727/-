package com.hbjt.fitness.agent.dto;

import com.hbjt.fitness.agent.domain.ValueSource;

/**
 * 健康指标（证据层）。这一层完全不依赖 LLM，所有数字都能手工复算。
 * <p>字段中的 XXXSource 用于标注数据来源（实测 / 估算 / 默认），
 * 避免把推算值当作实测值展示给教练和会员。
 */
public record HealthMetrics(

        /* ---------- 体格 ---------- */
        double bmi,
        String bmiCategory,

        double bodyFat,
        String bodyFatCategory,
        ValueSource bodyFatSource,

        /* ---------- 代谢 ---------- */
        /** 基础代谢率 kcal/日，Mifflin-St Jeor */
        double bmr,
        /** 每日总消耗 kcal/日 = BMR × 活动系数 */
        double tdee,
        /** 目标摄入 kcal/日 */
        double targetCalories,
        /** 蛋白质目标 g/日 */
        double proteinG,

        /* ---------- 计算过程参数（可审计） ---------- */
        String activityLevelLabel,
        double activityFactor,
        /** 目标热量是否触发了安全下限保护 */
        boolean calorieFloorApplied,

        /* ---------- 回显输入 ---------- */
        String genderLabel,
        int age,
        double height,
        double weight,
        String goalLabel
) {
}
