package com.hbjt.fitness.agent.service;

import com.hbjt.fitness.agent.domain.Gender;
import com.hbjt.fitness.agent.domain.Goal;
import com.hbjt.fitness.agent.domain.ValueSource;
import com.hbjt.fitness.agent.dto.HealthMetrics;
import com.hbjt.fitness.agent.dto.MemberProfile;
import org.springframework.stereotype.Service;

/**
 * 健康指标计算（证据层）。
 *
 * <p>这一层是系统可信度的根基：<b>所有数字类结论都在这里算出，绝不交给大模型编</b>。
 * 每个指标都能用公开公式手工复算（见 docs 里的算例表）。
 *
 * <p>公式与口径：
 * <ul>
 *   <li>BMI = 体重 / 身高²；分级用中国成人标准（&lt;18.5 偏瘦 / 18.5–23.9 正常 / 24–27.9 超重 / ≥28 肥胖）</li>
 *   <li>BMR：Mifflin-St Jeor（男 10W+6.25H−5A+5；女 10W+6.25H−5A−161）</li>
 *   <li>TDEE = BMR × 活动系数（默认中度 1.55）</li>
 *   <li>体脂率：优先用实测值；缺失时按 Deurenberg 公式估算并标注为「估算」</li>
 *   <li>目标热量 = TDEE ± 盈亏，并施加安全下限保护</li>
 *   <li>蛋白质：按目标取 1.6–2.0 g/kg；肥胖者按「标准体重」折算，避免按总体重过量</li>
 * </ul>
 */
@Service
public class HealthMetricsService {

    /** BMI 24 对应的「标准体重」基准，用于肥胖者蛋白质折算 */
    private static final double REFERENCE_BMI = 24.0;

    /** 每日摄入安全下限（kcal） */
    private static final double MIN_CALORIES_FEMALE = 1200;
    private static final double MIN_CALORIES_MALE = 1500;

    public HealthMetrics compute(MemberProfile p) {
        double heightM = p.height() / 100.0;
        double bmi = round1(p.weight() / (heightM * heightM));

        // ---------- 体脂率：实测 / 估算 ----------
        double bodyFat;
        ValueSource bodyFatSource;
        if (p.bodyFat() != null && p.bodyFat() > 0) {
            bodyFat = round1(p.bodyFat());
            bodyFatSource = ValueSource.MEASURED;
        } else {
            bodyFat = round1(clamp(deurenberg(bmi, p.age(), p.gender()), 3, 60));
            bodyFatSource = ValueSource.ESTIMATED;
        }

        // ---------- 基础代谢（Mifflin-St Jeor） ----------
        double bmrRaw = 10 * p.weight() + 6.25 * p.height() - 5 * p.age()
                + (p.gender() == Gender.MALE ? 5 : -161);
        double bmr = Math.round(bmrRaw);

        // ---------- 每日总消耗 ----------
        double tdee = Math.round(bmrRaw * p.activityLevel().factor());

        // ---------- 目标热量（含安全下限保护） ----------
        double rawTarget = targetCalories(p.goal(), tdee);
        double safeFloor = Math.max(
                p.gender() == Gender.FEMALE ? MIN_CALORIES_FEMALE : MIN_CALORIES_MALE,
                Math.round(bmrRaw * 0.8));
        boolean floorApplied = rawTarget < safeFloor;
        double target = floorApplied ? Math.round(safeFloor) : Math.round(rawTarget);

        // ---------- 蛋白质 ----------
        double proteinBaseWeight = proteinBaseWeight(p, heightM, bmi);
        double protein = round1(proteinFactor(p.goal()) * proteinBaseWeight);

        return new HealthMetrics(
                bmi, bmiCategory(bmi),
                bodyFat, bodyFatCategory(p.gender(), bodyFat), bodyFatSource,
                bmr, tdee, target, protein,
                p.activityLevel().label(), p.activityLevel().factor(), floorApplied,
                p.gender().label(), p.age(), p.height(), p.weight(), p.goal().label());
    }

    /* ================= 分类 ================= */

    /** 中国成人 BMI 标准 */
    private String bmiCategory(double bmi) {
        if (bmi < 18.5) return "偏瘦";
        if (bmi < 24) return "正常";
        if (bmi < 28) return "超重";
        return "肥胖";
    }

    /** 体脂分级（按性别，取常用成年人体脂参考区间） */
    private String bodyFatCategory(Gender g, double bf) {
        if (g == Gender.MALE) {
            if (bf < 6) return "偏低";
            if (bf <= 20) return "正常";
            if (bf <= 25) return "偏高";
            return "过高";
        }
        if (bf < 14) return "偏低";
        if (bf <= 28) return "正常";
        if (bf <= 32) return "偏高";
        return "过高";
    }

    /**
     * Deurenberg 体脂率估算公式：
     * BF% = 1.20 × BMI + 0.23 × 年龄 − 10.8 × 性别 − 5.4（男性性别=1，女性=0）
     * <p>仅用于缺失实测值时的兜底，结果会标注为「估算」。
     */
    private double deurenberg(double bmi, int age, Gender gender) {
        double sex = gender == Gender.MALE ? 1.0 : 0.0;
        return 1.20 * bmi + 0.23 * age - 10.8 * sex - 5.4;
    }

    /* ================= 热量与蛋白质 ================= */

    private double targetCalories(Goal goal, double tdee) {
        return switch (goal) {
            case LOSE_FAT -> tdee - 500;
            case GAIN_MUSCLE -> tdee + 300;
            case SHAPE -> tdee - 200;
            case MAINTAIN -> tdee;
        };
    }

    private double proteinFactor(Goal goal) {
        return switch (goal) {
            case LOSE_FAT, GAIN_MUSCLE, SHAPE -> 2.0;
            case MAINTAIN -> 1.6;
        };
    }

    /**
     * 蛋白质折算体重：BMI ≥ 28 时改用「BMI 24 对应的标准体重」，
     * 避免按总体重乘 2.0 g/kg 得出对肥胖者过高（有时超过 250g）的目标。
     */
    private double proteinBaseWeight(MemberProfile p, double heightM, double bmi) {
        if (bmi < 28) {
            return p.weight();
        }
        return Math.min(p.weight(), REFERENCE_BMI * heightM * heightM);
    }

    /* ================= 工具 ================= */

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
