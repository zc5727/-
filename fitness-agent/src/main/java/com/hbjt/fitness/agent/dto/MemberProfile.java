package com.hbjt.fitness.agent.dto;

import com.hbjt.fitness.agent.domain.ActivityLevel;
import com.hbjt.fitness.agent.domain.Gender;
import com.hbjt.fitness.agent.domain.Goal;
import com.hbjt.fitness.agent.domain.ProfileSource;

/**
 * 已完成校验与合并的会员画像（指标计算的唯一输入）。
 * <p>相比裸请求，这里保证 gender/age/height/weight/goal 一定非空且已过范围校验，
 * 下游计算无需再判空——消除原实现中「memberId 路径绕过校验」导致的除零/NaN 问题。
 */
public record MemberProfile(
        Long memberNo,
        String name,
        Gender gender,
        int age,
        double height,
        double weight,
        /** 体脂率，可能为 null（业务库无此字段且用户未填） */
        Double bodyFat,
        Goal goal,
        ActivityLevel activityLevel,
        ProfileSource source
) {
}
