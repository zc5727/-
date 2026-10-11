package com.hbjt.fitness.agent.service;

import com.hbjt.fitness.agent.domain.ActivityLevel;
import com.hbjt.fitness.agent.domain.Gender;
import com.hbjt.fitness.agent.domain.Goal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户自然语言里抽取身体数据的「槽位」（性别 / 年龄 / 身高 / 体重 / 体脂 / 目标 / 活动水平）。
 *
 * <p><b>为什么用规则而不是让大模型抽</b>：抽取环节如果依赖大模型，
 * 那么在未配置密钥、网络抖动、模型抽错时，用户连"把信息说完"都做不到。
 * 规则抽取零依赖、可复现、毫秒级，保证对话在任何情况下都能推进；
 * 大模型只用在最后一步——把已经算好的数字组织成计划文案。
 *
 * <p>抽取顺序刻意设计为「带标签 &gt; 带单位 &gt; 换算」：
 * 例如「身高 175」靠标签命中，「175cm」靠单位命中，「1.75 米」换算成 175cm，
 * 「140 斤」换算成 70kg。数值超出合理区间的一律不采信，并给出提示，
 * 避免把「体重 500」这种口误算进指标（原实现正是在这类输入上崩掉的）。
 */
@Component
public class ChatSlotExtractor {

    /* ---------- 合理区间：与后端校验注解保持同一口径 ---------- */
    private static final int AGE_MIN = 10, AGE_MAX = 100;
    private static final double HEIGHT_MIN = 100, HEIGHT_MAX = 250;
    private static final double WEIGHT_MIN = 25, WEIGHT_MAX = 300;
    private static final double BODY_FAT_MIN = 3, BODY_FAT_MAX = 60;

    /* ---------- 性别 ---------- */
    private static final Pattern MALE =
            Pattern.compile("(?<![a-z])(男生|男的|男性|男士|男|male|man)(?![a-z])");
    private static final Pattern FEMALE =
            Pattern.compile("(?<![a-z])(女生|女的|女性|女士|女|female|woman)(?![a-z])");

    /* ---------- 年龄 ---------- */
    private static final Pattern AGE_LABEL =
            Pattern.compile("(?:年龄|年纪)[:：是为约有\\s]*(\\d{1,3})");
    private static final Pattern AGE_UNIT =
            Pattern.compile("(\\d{1,3})\\s*(?:岁|周岁|years?|y/o|yo)");

    /* ---------- 身高 ---------- */
    private static final Pattern HEIGHT_LABEL =
            Pattern.compile("(?:身高|高度)[:：是为约有\\s]*(\\d{2,3}(?:\\.\\d{1,2})?)");
    private static final Pattern HEIGHT_CM =
            Pattern.compile("(\\d{2,3}(?:\\.\\d{1,2})?)\\s*(?:cm|厘米|公分)");
    private static final Pattern HEIGHT_M =
            Pattern.compile("(?<![a-z0-9.])(\\d(?:\\.\\d{1,2})?)\\s*(?:m|米)(?![a-z])");

    /* ---------- 体重 ---------- */
    private static final Pattern WEIGHT_LABEL =
            Pattern.compile("(?:体重|重量)[:：是为约有\\s]*(\\d{2,3}(?:\\.\\d{1,2})?)");
    private static final Pattern WEIGHT_KG =
            Pattern.compile("(\\d{2,3}(?:\\.\\d{1,2})?)\\s*(?:kg|公斤|千克)");
    private static final Pattern WEIGHT_JIN =
            Pattern.compile("(\\d{2,3}(?:\\.\\d{1,2})?)\\s*斤");
    private static final Pattern WEIGHT_LB =
            Pattern.compile("(\\d{2,3}(?:\\.\\d{1,2})?)\\s*(?:lb|lbs|磅)");

    /* ---------- 体脂率 ---------- */
    private static final Pattern BODY_FAT_LABEL =
            Pattern.compile("(?:体脂率|体脂|脂肪率)[:：是为约有\\s]*(\\d{1,2}(?:\\.\\d{1,2})?)");
    private static final Pattern BODY_FAT_PCT =
            Pattern.compile("(\\d{1,2}(?:\\.\\d{1,2})?)\\s*(?:%|％|百分)");

    /* ---------- 目标：按此优先级取第一个命中的 ---------- */
    private static final Object[][] GOAL_KEYWORDS = {
            {Goal.LOSE_FAT, new String[]{"减脂", "减肥", "瘦身", "掉秤", "降脂", "减重", "想瘦", "瘦下来", "控制体重"}},
            {Goal.GAIN_MUSCLE, new String[]{"增肌", "长肌肉", "增重", "变壮", "变强", "长肌"}},
            {Goal.SHAPE, new String[]{"塑形", "线条", "紧致", "马甲线", "身材管理"}},
            {Goal.MAINTAIN, new String[]{"维持", "保持", "不变", "维稳", "不增不减"}},
    };

    /* ---------- 活动水平：从具体到宽泛，避免"每周"这类泛词误命中 ---------- */
    private static final Object[][] ACTIVITY_KEYWORDS = {
            {ActivityLevel.VERY_ACTIVE, new String[]{"一天两练", "一天练两次", "体力工作", "极高强度", "运动员"}},
            {ActivityLevel.ACTIVE, new String[]{"每周6-7次", "每周6～7次", "每周六到七次", "每天运动", "每天都练", "几乎每天", "天天练"}},
            {ActivityLevel.MODERATE, new String[]{"每周3-5次", "每周3～5次", "每周三到五次", "每周3到5次"}},
            {ActivityLevel.LIGHT, new String[]{"每周1-3次", "每周1～3次", "每周一到三次", "偶尔运动", "偶尔练", "一周一两次", "很少练"}},
            {ActivityLevel.SEDENTARY, new String[]{"久坐", "不运动", "几乎不动", "很少运动", "不爱运动", "坐办公室", "没有运动"}},
    };

    /** 「每周练 4 次 / 一周三次」的通用写法 */
    private static final Pattern WEEKLY_TIMES = Pattern.compile(
            "(?:每|一)?\\s*周\\s*(?:练|运动|健身|去|练个)?\\s*([0-9]|[一二两三四五六七八九])\\s*次");
    /** 不带「周」的口语说法「练两次」 */
    private static final Pattern BARE_TIMES = Pattern.compile(
            "(?:练|运动|健身)\\s*([0-9]|[一二两三四五六七八九])\\s*次");

    /** 可变的槽位袋，单次请求内使用。 */
    public static class Slots {
        public Gender gender;
        public Integer age;
        public Double height;
        public Double weight;
        public Double bodyFat;
        public Goal goal;
        public ActivityLevel activityLevel;
    }

    /**
     * @param filled   本次新听懂并写入的字段名集合（gender/age/height/weight/bodyFat/goal/activityLevel）
     * @param warnings 面向用户的提示（如数值超范围）
     */
    public record Extracted(Set<String> filled, List<String> warnings) {
    }

    public Extracted extract(String rawText, Slots slots) {
        Set<String> filled = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return new Extracted(filled, warnings);
        }
        String t = normalize(rawText);

        extractGender(t, slots, filled);
        extractAge(t, slots, filled, warnings);
        extractHeight(t, slots, filled, warnings);
        extractWeight(t, slots, filled, warnings);
        extractBodyFat(t, slots, filled, warnings);
        extractGoal(t, slots, filled);
        extractActivity(t, slots, filled);

        return new Extracted(filled, warnings);
    }

    private void extractGender(String t, Slots s, Set<String> filled) {
        if (s.gender != null) {
            return;
        }
        if (FEMALE.matcher(t).find()) {
            s.gender = Gender.FEMALE;
            filled.add("gender");
        } else if (MALE.matcher(t).find()) {
            s.gender = Gender.MALE;
            filled.add("gender");
        }
    }

    private void extractAge(String t, Slots s, Set<String> filled, List<String> warnings) {
        if (s.age != null) {
            return;
        }
        Integer v = firstInt(AGE_LABEL, t);
        if (v == null) {
            v = firstInt(AGE_UNIT, t);
        }
        if (v == null) {
            return;
        }
        if (v < AGE_MIN || v > AGE_MAX) {
            warnings.add("年龄 " + v + " 超出了 " + AGE_MIN + "-" + AGE_MAX + " 岁的范围，请核对一下");
            return;
        }
        s.age = v;
        filled.add("age");
    }

    private void extractHeight(String t, Slots s, Set<String> filled, List<String> warnings) {
        if (s.height != null) {
            return;
        }
        Double v = firstDouble(HEIGHT_LABEL, t);
        if (v == null) {
            v = firstDouble(HEIGHT_CM, t);
        }
        if (v == null) {
            Double meters = firstDouble(HEIGHT_M, t);
            if (meters != null) {
                v = meters * 100;   // 1.75 米 → 175 cm
            }
        }
        if (v == null) {
            return;
        }
        if (v < HEIGHT_MIN || v > HEIGHT_MAX) {
            warnings.add("身高 " + trim(v) + " 超出了 " + (int) HEIGHT_MIN + "-" + (int) HEIGHT_MAX + " cm 的范围，请核对一下");
            return;
        }
        s.height = v;
        filled.add("height");
    }

    private void extractWeight(String t, Slots s, Set<String> filled, List<String> warnings) {
        if (s.weight != null) {
            return;
        }
        Double v = firstDouble(WEIGHT_LABEL, t);
        String note = null;
        if (v == null) {
            v = firstDouble(WEIGHT_KG, t);
        }
        if (v == null) {
            Double jin = firstDouble(WEIGHT_JIN, t);
            if (jin != null) {
                v = jin / 2;    // 1 斤 = 0.5 kg
                note = "（已按 1 斤 = 0.5 kg 换算）";
            }
        }
        if (v == null) {
            Double lb = firstDouble(WEIGHT_LB, t);
            if (lb != null) {
                v = lb * 0.4536;
                note = "（已按 1 磅 ≈ 0.454 kg 换算）";
            }
        }
        if (v == null) {
            return;
        }
        if (v < WEIGHT_MIN || v > WEIGHT_MAX) {
            warnings.add("体重 " + trim(v) + " kg 超出了 " + (int) WEIGHT_MIN + "-" + (int) WEIGHT_MAX + " kg 的范围，请核对一下");
            return;
        }
        s.weight = v;
        filled.add("weight");
        if (note != null) {
            warnings.add("体重按 " + trim(v) + " kg 记录" + note);
        }
    }

    private void extractBodyFat(String t, Slots s, Set<String> filled, List<String> warnings) {
        if (s.bodyFat != null) {
            return;
        }
        Double v = firstDouble(BODY_FAT_LABEL, t);
        if (v == null) {
            v = firstDouble(BODY_FAT_PCT, t);
        }
        if (v == null) {
            return;
        }
        if (v < BODY_FAT_MIN || v > BODY_FAT_MAX) {
            warnings.add("体脂率 " + trim(v) + "% 超出了 " + (int) BODY_FAT_MIN + "-" + (int) BODY_FAT_MAX + "% 的范围，请核对一下");
            return;
        }
        s.bodyFat = v;
        filled.add("bodyFat");
    }

    private void extractGoal(String t, Slots s, Set<String> filled) {
        if (s.goal != null) {
            return;
        }
        for (Object[] row : GOAL_KEYWORDS) {
            Goal g = (Goal) row[0];
            for (String kw : (String[]) row[1]) {
                if (t.contains(kw)) {
                    s.goal = g;
                    filled.add("goal");
                    return;
                }
            }
        }
    }

    private void extractActivity(String t, Slots s, Set<String> filled) {
        if (s.activityLevel != null) {
            return;
        }
        for (Object[] row : ACTIVITY_KEYWORDS) {
            ActivityLevel a = (ActivityLevel) row[0];
            for (String kw : (String[]) row[1]) {
                if (t.contains(kw)) {
                    s.activityLevel = a;
                    filled.add("activityLevel");
                    return;
                }
            }
        }
        // 关键词表兜不住口语化的次数表达（"每周练4次""想练两次"）。
        // 放在关键词表之后：像"一天练两次"这种更极端的说法要先被表里的 VERY_ACTIVE 拿走。
        Integer times = parseWeeklyTimes(t);
        if (times != null && times >= 1 && times <= 9) {
            s.activityLevel = times >= 6 ? ActivityLevel.ACTIVE
                    : times >= 3 ? ActivityLevel.MODERATE
                    : ActivityLevel.LIGHT;
            filled.add("activityLevel");
        }
    }

    /** 「每周练 4 次 / 一周三次 / 想练两次」→ 每周次数；识别不到返回 null */
    private Integer parseWeeklyTimes(String t) {
        Matcher m = WEEKLY_TIMES.matcher(t);
        if (m.find()) {
            return cnNumber(m.group(1));
        }
        // 不带"周"的口语（"练两次"），但排除"一天练两次"这类按天计的说法
        if (!t.contains("天")) {
            Matcher m2 = BARE_TIMES.matcher(t);
            if (m2.find()) {
                return cnNumber(m2.group(1));
            }
        }
        return null;
    }

    private Integer cnNumber(String s) {
        return switch (s) {
            case "一" -> 1;
            case "两", "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> {
                try {
                    yield Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    /* ==================== 工具方法 ==================== */

    /** 全角转半角、统一波浪线/破折号，便于同一套正则匹配。 */
    private String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else if (c >= 'Ａ' && c <= 'Ｚ') {
                sb.append((char) (c - 'Ａ' + 'a'));
            } else if (c >= 'ａ' && c <= 'ｚ') {
                sb.append(c);
            } else if (c == '～' || c == '~' || c == '—') {
                sb.append('-');
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private Integer firstInt(Pattern p, String t) {
        Matcher m = p.matcher(t);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private Double firstDouble(Pattern p, String t) {
        Matcher m = p.matcher(t);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
