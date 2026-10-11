package com.hbjt.fitness.agent.dto;

import com.hbjt.fitness.agent.domain.ActivityLevel;
import com.hbjt.fitness.agent.domain.Gender;
import com.hbjt.fitness.agent.domain.Goal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 健身计划请求。两种用法：
 * <ol>
 *   <li><b>按档案</b>：只给 {@code memberId}，其余从业务系统读取（未读到的字段可用本次入参覆盖）</li>
 *   <li><b>直接录入</b>：不给 {@code memberId}，则 gender/age/height/weight/goal 必填</li>
 * </ol>
 * 所有范围约束对「提供了值」的情况一律生效——两条路径都拦得住非法数值。
 * 注：Jakarta Validation 对 null 不校验，因此这些注解恰好只约束「填了的值」。
 */
public record PlanRequest(

        /** 业务系统（gym-management-system）中的会员编号 */
        Long memberId,

        /** 会员称呼，可为空 */
        String name,

        Gender gender,

        @Min(value = 10, message = "年龄需在 10-100 之间")
        @Max(value = 100, message = "年龄需在 10-100 之间")
        Integer age,

        /** 身高 cm */
        @DecimalMin(value = "100.0", message = "身高需在 100-250 cm 之间")
        @DecimalMax(value = "250.0", message = "身高需在 100-250 cm 之间")
        Double height,

        /** 体重 kg */
        @DecimalMin(value = "25.0", message = "体重需在 25-300 kg 之间")
        @DecimalMax(value = "300.0", message = "体重需在 25-300 kg 之间")
        Double weight,

        /** 体脂率 %；可不填，届时按 Deurenberg 公式估算并标注为「估算」 */
        @DecimalMin(value = "3.0", message = "体脂率需在 3-60 % 之间")
        @DecimalMax(value = "60.0", message = "体脂率需在 3-60 % 之间")
        Double bodyFat,

        /** 活动水平，缺省为中度（系数 1.55） */
        ActivityLevel activityLevel,

        Goal goal
) {
}
