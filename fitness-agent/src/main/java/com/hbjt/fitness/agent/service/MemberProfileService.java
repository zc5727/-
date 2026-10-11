package com.hbjt.fitness.agent.service;

import com.hbjt.fitness.agent.client.GymMember;
import com.hbjt.fitness.agent.client.GymMemberClient;
import com.hbjt.fitness.agent.domain.ActivityLevel;
import com.hbjt.fitness.agent.domain.Gender;
import com.hbjt.fitness.agent.domain.ProfileSource;
import com.hbjt.fitness.agent.dto.MemberProfile;
import com.hbjt.fitness.agent.dto.PlanRequest;
import com.hbjt.fitness.agent.support.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 会员画像解析：把「按会员编号」与「直接录入」两条路径统一成一份经过校验的画像。
 *
 * <p>合并规则（会员编号路径）：
 * <ul>
 *   <li>性别 / 年龄 / 身高 / 体重：优先取请求里的覆盖值，否则用档案值</li>
 *   <li>体脂率：业务库无此字段，只能来自请求；未提供则该值为 null，由指标层估算</li>
 *   <li>健身目标：不在会员档案中，无论哪条路径都必须由请求给出</li>
 * </ul>
 * 关键点：<b>两条路径最终都要过同一套必填与范围校验</b>，
 * 不允许出现「按编号取数就不校验」的旁路。
 */
@Service
public class MemberProfileService {

    private static final Logger log = LoggerFactory.getLogger(MemberProfileService.class);

    private static final ActivityLevel DEFAULT_ACTIVITY = ActivityLevel.MODERATE;

    private final GymMemberClient gymMemberClient;

    public MemberProfileService(GymMemberClient gymMemberClient) {
        this.gymMemberClient = gymMemberClient;
    }

    public MemberProfile resolve(PlanRequest req) {
        if (req.goal() == null) {
            throw new BadRequestException("请选择健身目标（goal）");
        }
        ActivityLevel activity = req.activityLevel() == null ? DEFAULT_ACTIVITY : req.activityLevel();

        if (req.memberId() != null) {
            return fromMemberRecord(req, activity);
        }
        return fromInline(req, activity);
    }

    private MemberProfile fromInline(PlanRequest req, ActivityLevel activity) {
        List<String> missing = new ArrayList<>();
        if (req.gender() == null) missing.add("gender");
        if (req.age() == null) missing.add("age");
        if (req.height() == null) missing.add("height");
        if (req.weight() == null) missing.add("weight");
        if (!missing.isEmpty()) {
            throw new BadRequestException("缺少必填字段：" + String.join(" / ", missing)
                    + "（或改用 memberId 从会员档案读取）");
        }
        return new MemberProfile(
                null,
                blankToNull(req.name()),
                req.gender(),
                req.age(),
                req.height(),
                req.weight(),
                req.bodyFat(),
                req.goal(),
                activity,
                ProfileSource.INLINE);
    }

    private MemberProfile fromMemberRecord(PlanRequest req, ActivityLevel activity) {
        GymMember m = gymMemberClient.fetchByMemberNo(req.memberId());
        log.info("已读取会员档案 memberNo={} name={}", m.memberNo(), m.name());

        Gender gender = req.gender() != null ? req.gender() : Gender.fromGymCode(m.genderCode());
        Integer age = req.age() != null ? req.age() : m.age();
        Double height = req.height() != null ? req.height() : m.height();
        Double weight = req.weight() != null ? req.weight() : m.weight();

        List<String> missing = new ArrayList<>();
        if (gender == null) missing.add("性别");
        if (age == null) missing.add("年龄");
        if (height == null) missing.add("身高");
        if (weight == null) missing.add("体重");
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "会员档案缺少以下信息，请在请求中补充：" + String.join(" / ", missing),
                    "memberNo=" + req.memberId() + " 档案字段不完整");
        }

        return new MemberProfile(
                m.memberNo(),
                req.name() != null && !req.name().isBlank() ? req.name() : blankToNull(m.name()),
                gender,
                age,
                height,
                weight,
                // 业务库没有体脂率，只能来自请求
                req.bodyFat(),
                req.goal(),
                activity,
                ProfileSource.MEMBER_RECORD);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
