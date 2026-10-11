package com.hbjt.fitness.agent.service;

import com.hbjt.fitness.agent.domain.Goal;
import com.hbjt.fitness.agent.domain.ValueSource;
import com.hbjt.fitness.agent.dto.FitnessPlan;
import com.hbjt.fitness.agent.dto.HealthMetrics;
import com.hbjt.fitness.agent.dto.MemberProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则引擎：大模型不可用时的兜底计划生成器。
 *
 * <p>与「模板化套话」的区别在于：每一条建议都由 {@link HealthMetrics} 的具体数值触发，
 * 因此输出的计划与会员的实际身体数据是绑定的，而不是固定文案。
 */
@Component
public class RuleBasedPlanBuilder {

    public FitnessPlan build(HealthMetrics m, MemberProfile p) {
        return new FitnessPlan(
                buildSummary(m, p),
                buildTraining(m, p),
                buildDiet(m, p),
                buildPrecautions(m, p));
    }

    /* ---------------- 总结 ---------------- */

    private String buildSummary(HealthMetrics m, MemberProfile p) {
        String name = p.name() == null ? "该会员" : p.name();
        String bfPart = m.bodyFatSource() == ValueSource.ESTIMATED
                ? String.format("体脂率约 %.1f%%（估算值）", m.bodyFat())
                : String.format("体脂率 %.1f%%", m.bodyFat());
        return String.format(
                "%s：BMI %.1f（%s），%s（%s）。基础代谢约 %.0f kcal/日，日常消耗约 %.0f kcal/日，"
                        + "围绕「%s」目标建议每日摄入约 %.0f kcal、蛋白质 %.0f g。",
                name, m.bmi(), m.bmiCategory(), bfPart, m.bodyFatCategory(),
                m.bmr(), m.tdee(), m.goalLabel(), m.targetCalories(), m.proteinG());
    }

    /* ---------------- 训练 ---------------- */

    private List<String> buildTraining(HealthMetrics m, MemberProfile p) {
        List<String> out = new ArrayList<>();

        switch (p.goal()) {
            case LOSE_FAT -> {
                out.add("每周 4–5 练：3 次全身力量（深蹲/硬拉/卧推/划船等复合动作）+ 2 次有氧");
                out.add("力量训练每组 8–12 次、3–4 组，组间休息 60–90 秒；有氧每次 30–40 分钟中等强度");
            }
            case GAIN_MUSCLE -> {
                out.add("每周 4–5 练：推 / 拉 / 腿分化，每个部位每周 2 次训练频率");
                out.add("主项每组 6–10 次、3–5 组，追求渐进超负荷（每 1–2 周小幅加重或加次）");
            }
            case SHAPE -> {
                out.add("每周 4 练：2 次力量 + 2 次循环训练（力量与有氧交替，维持较高心率）");
                out.add("以中低负荷、高次数为主，注重动作质量与核心稳定");
            }
            case MAINTAIN -> {
                out.add("每周 3 练：全身力量与有氧各半，保持活动量稳定");
                out.add("可加入 1–2 次运动项目（球类/游泳/骑行）提高可持续性");
            }
        }

        // 依 BMI 分档给出针对性修正
        if (m.bmi() >= 28) {
            out.add("BMI 已达肥胖区间，初期以快走、椭圆机、游泳等低冲击有氧建立习惯，减少跳跃类冲击");
            out.add("力量训练从器械或自重开始，先建立动作模式，避免过早大重量");
        } else if (m.bmi() >= 24) {
            out.add("BMI 处于超重区间，建议每次训练后追加 15 分钟稳态有氧，提高热量消耗");
        } else if (m.bmi() < 18.5) {
            out.add("BMI 偏瘦，以力量训练为主、有氧控制在每周 2 次以内，避免热量过度消耗");
        }

        // 依体脂分档
        if ("过高".equals(m.bodyFatCategory()) && m.bmi() < 28) {
            out.add("体脂率偏高但体重正常（隐性肥胖倾向），优先增加力量训练与蛋白质摄入，而非单纯减重");
        }

        // 依活动水平
        if (m.activityFactor() <= 1.2) {
            out.add("当前日常活动量偏低，建议先从每日 6000–8000 步的日常活动量补齐");
        }

        out.add("每次训练包含 5–10 分钟动态热身与 5 分钟静态拉伸");
        return out;
    }

    /* ---------------- 饮食 ---------------- */

    private List<String> buildDiet(HealthMetrics m, MemberProfile p) {
        List<String> out = new ArrayList<>();

        out.add(String.format("每日热量目标 %.0f kcal（基础代谢 %.0f kcal，日常消耗 %.0f kcal）",
                m.targetCalories(), m.bmr(), m.tdee()));

        if (m.calorieFloorApplied()) {
            out.add("注意：按目标计算的热量已低于安全下限，已自动上调至安全区间，请勿再自行减少进食量");
        }

        out.add(String.format("蛋白质 %.0f g/日，分散到 4–5 餐摄入（约每餐 %.0f g），优先鸡胸/鱼虾/蛋/奶/豆制品",
                m.proteinG(), m.proteinG() / 4));

        switch (p.goal()) {
            case LOSE_FAT -> {
                out.add("保持每日 400–500 kcal 热量缺口，主食替换为全谷物/薯类，控制在每餐一拳头量");
                out.add("蔬菜每日 400–500 g，水果 200 g 左右，避免含糖饮料与油炸食品");
            }
            case GAIN_MUSCLE -> {
                out.add("保持每日 200–300 kcal 热量盈余，训练后 1 小时内补充碳水 + 蛋白质");
                out.add("碳水可选米饭/面食/燕麦，约占热量的 45–55%，保证训练强度");
            }
            case SHAPE -> {
                out.add("热量小幅缺口，碳水与蛋白质配比均衡，控制精制糖摄入");
                out.add("增加膳食纤维摄入，有助于控制体脂并改善饱腹感");
            }
            case MAINTAIN -> {
                out.add("保持热量收支平衡，三餐规律，脂肪以不饱和脂肪为主");
            }
        }

        out.add("每日饮水 2000–2500 ml，训练日适当增加；睡眠保证 7 小时以上");
        return out;
    }

    /* ---------------- 注意事项 ---------------- */

    private List<String> buildPrecautions(HealthMetrics m, MemberProfile p) {
        List<String> out = new ArrayList<>();

        out.add("本计划基于身高、体重、年龄、性别与目标自动生成，非医疗建议；如有心血管、关节、代谢类疾病请先咨询医生");

        if (m.bodyFatSource() == ValueSource.ESTIMATED) {
            out.add("体脂率为公式估算值而非实测，建议使用体脂秤或体测仪复测后重新评估");
        }
        if ("过高".equals(m.bodyFatCategory())) {
            out.add("体脂率偏高，饮食控制与有氧训练的权重应高于单纯增加训练量");
        }
        if (m.bmi() < 18.5) {
            out.add("体重偏轻，不建议在未增重的前提下加大有氧训练量");
        }
        if (p.age() >= 50) {
            out.add("年龄偏大，训练前充分热身，避免憋气与大负重，优先保证动作安全");
        }
        if (p.age() < 18) {
            out.add("未成年会员，训练应以动作学习与兴趣培养为主，避免大重量力量训练");
        }

        out.add("建议每 4 周复测体重与体脂，按变化调整热量与训练安排");
        out.add("出现头晕、关节疼痛、持续疲劳等信号时及时停止训练并就医");
        return out;
    }
}
