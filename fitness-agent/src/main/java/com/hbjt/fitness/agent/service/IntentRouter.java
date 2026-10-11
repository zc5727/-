package com.hbjt.fitness.agent.service;

import com.hbjt.fitness.agent.domain.Intent;
import com.hbjt.fitness.agent.dto.PendingAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 意图路由。
 *
 * <p>业务动作只有七件，所以这里不追求"能理解任意说法"，而是追求<b>不认错</b>。
 * 三条硬规则：
 *
 * <ol>
 *   <li><b>层序不可越</b>：等待态短答 → 查询类（账户数据 / 知识问答）→ 动作类 → 兜底。
 *       「充值记录」里虽然有「充值」，但它先被查询层截走，绝不会变成发起充值；
 *       同理「减脂期饮食怎么安排」会被知识层截走，而不是当成"要生成减脂计划"。</li>
 *   <li><b>状态给了短答含义</b>：「好」在平时毫无信息量，但在等确认时就等于「确认」。
 *       因此短答识别必须限定在字符串很短（去标点 ≤ 8 字）且全量匹配词表时才成立，
 *       「好，我报了哪些课」不会被误判为确认。</li>
 *   <li><b>认不出就说认不出</b>：落到 UNKNOWN 明确告知并列出能力清单，绝不猜。</li>
 * </ol>
 *
 * <p>金额识别一律允许偏差：识别出的金额一定会在确认卡上原样展示，
 * 由用户点头确认，因此"听成一个不想要的数字"不会造成实际损失。
 */
@Component
public class IntentRouter {

    /* ==================== 词表 ==================== */

    private static final String[] CONFIRM_WORDS = {
            "好", "好的", "好滴", "行", "可以", "可", "嗯", "嗯嗯", "是", "是的", "对", "对的",
            "确定", "确认", "没问题", "就这样", "继续", "ok", "okay", "要", "是的没错", "没错",
            "去支付", "支付", "出二维码", "给我二维码", "下单", "付"
    };

    private static final String[] CANCEL_WORDS = {
            "算了", "不用了", "不要了", "取消", "不", "不了", "不充了", "不充", "别", "不用",
            "退出", "停止", "cancel", "no", "先不要", "再说吧"
    };

    /** 等支付态下表示「我已经付了」 */
    private static final String[] PAID_CLAIM_WORDS = {
            "付好", "付完", "付了", "已付", "支付成功", "付好了", "扫完", "扫码完成", "搞定", "好了",
            "付过了", "支付好了", "已经支付"
    };

    /** 充值记录查询的限定词——必须与充值词共现 */
    private static final String[] RECHARGE_QUERY_WORDS = {
            "记录", "历史", "明细", "流水", "几次", "多少次", "一共", "总共", "账单", "清单",
            "充过多少", "充了多少", "充过几次", "消费记录", "明细"
    };

    private static final String[] RECHARGE_WORDS = {"充值", "充钱", "充过", "续费", "充一点", "充点"};

    private static final String[] BALANCE_WORDS = {
            "余额", "卡里", "账户", "剩余", "还剩", "还有多少钱", "有多少钱", "剩多少钱", "我的钱", "钱包"
    };

    private static final String[] COURSE_QUERY_WORDS = {
            "我的课程", "我的课", "哪些课", "什么课", "已报", "报了", "报名记录", "课程记录", "上过什么"
    };

    private static final String[] REGISTER_WORDS = {"报名", "我要报", "想报", "报个课", "约课", "买个课"};
    private static final String[] CHECKIN_WORDS = {"签到", "打卡", "签个到", "考勤", "我今天来了"};
    private static final String[] PLAN_WORDS = {
            "减脂", "减肥", "增肌", "长肌肉", "塑形", "紧致", "健身计划", "计划", "怎么练", "练什么",
            "训练", "饮食", "食谱", "锻炼", "体脂", "维持", "保持身材", "瘦下来", "想瘦", "变壮",
            // 通用健身诉求：此前只有"维持/保持身材"，"保持健康"这类最普通的说法反而落进兜底
            "保持健康", "身体健康", "增强体质", "健身", "运动", "体能", "练一练", "练练"
    };
    private static final String[] HELP_WORDS = {
            "你能做什么", "能干什么", "会什么", "你能干嘛", "帮助", "怎么用", "功能", "help", "能力"
    };

    /* ---------- 知识库问答：主题词 + 疑问词，两者同时命中才算 ---------- */

    /**
     * 知识主题词。刻意只用「具体的健身术语」，不收"吃""药""练"这类单字——
     * 单字召回会把「你觉得我该吃什么药」这种本该兜底的话也拉进知识库，
     * 而在非健身语境下硬答知识，比老实说"没听懂"更糟。
     */
    private static final String[] KNOWLEDGE_TOPIC_WORDS = {
            // 动作
            "深蹲", "硬拉", "卧推", "引体", "划船", "推举", "弯举", "平板支撑", "卷腹", "臀桥",
            "箭步蹲", "提踵", "飞鸟", "下压", "下拉", "侧平举", "俯卧撑", "超人式", "死虫", "鸟狗",
            // 训练组织
            "训练", "动作", "姿势", "发力", "呼吸", "组间", "组数", "次数", "力竭", "超级组",
            "递减", "金字塔", "热身", "拉伸", "放松", "有氧", "无氧", "抗阻", "核心",
            // 部位 / 伤病
            "膝盖", "腰椎", "腰部", "肩关节", "肩颈", "手腕", "关节", "受伤", "拉伤", "扭伤",
            "护具", "腰带", "护膝", "恢复", "睡眠",
            // 营养
            "蛋白质", "碳水", "脂肪", "热量", "卡路里", "基础代谢", "营养", "饮食", "加餐",
            "训练前", "训练后", "练前", "练后", "水分", "补水", "电解质",
            // 目标 / 阶段
            "增肌", "减脂", "燃脂", "塑形", "平台期", "瓶颈"
    };

    /** 疑问词：出现任意一个才把这句话当作「提问」而不是「下指令」 */
    private static final String[] QUESTION_WORDS = {
            "怎么", "怎么样", "如何", "为什么", "为啥", "是什么", "什么是", "多少", "多久",
            "几组", "几次", "几个", "哪些", "哪种", "哪个", "能不能", "可不可以", "要不要",
            "该不该", "需要吗", "注意什么", "注意事项", "好处", "坏处", "危害", "区别", "差别",
            "正确", "标准", "规范", "吗", "呢", "？", "?"
    };

    /**
     * 强计划诉求。出现这些词说明用户要的是"给我一份计划"，不是"问一个知识点"，
     * 因此知识问答层必须让位——否则「减脂计划怎么安排」会被拆成知识问答。
     */
    private static final String[] PLAN_REQUEST_WORDS = {
            "计划", "方案", "规划", "制定", "定制", "给我一份", "帮我做一份", "帮我出一份",
            "帮我安排", "帮我排", "排个"
    };

    /* ==================== 结果 ==================== */

    /**
     * @param intent 判定出的意图
     * @param amount 顺带抽到的金额（仅充值场景有意义），可为 null
     * @param reason 判定依据，写进日志便于事后复盘"为什么这句话被认成这个意图"
     */
    public record Decision(Intent intent, Integer amount, String reason) {
    }

    /* ==================== 主入口 ==================== */

    /**
     * @param lastText 用户本轮说的话
     * @param pending  上一轮留下的待办（等金额 / 等确认 / 等支付），可为 null
     */
    public Decision route(String lastText, PendingAction pending) {
        String t = normalize(lastText);

        // ---------- 第 1 层：等待态优先 ----------
        if (pending != null) {
            Decision d = routePending(t, pending);
            if (d != null) {
                return d;
            }
        }

        // ---------- 第 2 层：查询类，遮蔽动作类 ----------
        if (isRechargeQuery(t)) {
            return new Decision(Intent.QUERY_RECHARGE, null, "充值+查询限定词");
        }
        if (isCourseQuery(t)) {
            return new Decision(Intent.QUERY_COURSE, null, "课程+所属限定词");
        }
        if (containsAny(t, BALANCE_WORDS)) {
            return new Decision(Intent.QUERY_BALANCE, null, "余额相关词");
        }
        if (t.contains("积分")) {
            return new Decision(Intent.QUERY_INTEGRAL, null, "命中「积分」");
        }
        // 知识问答也在查询层：它是"只读地取知识"，同样不该被动作层抢走
        if (isKnowledgeQuestion(t)) {
            return new Decision(Intent.QUERY_KNOWLEDGE, null, "主题词+疑问词");
        }

        // ---------- 第 3 层：动作类 ----------
        Integer amount = extractAmount(t);
        if (containsAny(t, RECHARGE_WORDS) || amount != null) {
            return new Decision(Intent.RECHARGE, amount, amount != null ? "充值词+金额" : "充值词");
        }
        if (containsAny(t, REGISTER_WORDS)) {
            return new Decision(Intent.REGISTER_COURSE, null, "报名相关词");
        }
        if (containsAny(t, CHECKIN_WORDS)) {
            return new Decision(Intent.CHECKIN, null, "签到相关词");
        }
        if (containsAny(t, PLAN_WORDS)) {
            return new Decision(Intent.FITNESS_PLAN, null, "健身计划相关词");
        }

        // ---------- 第 4 层：兜底 ----------
        if (containsAny(t, HELP_WORDS)) {
            return new Decision(Intent.HELP, null, "帮助词");
        }
        return new Decision(Intent.UNKNOWN, null, "无任何规则命中");
    }

    /* ==================== 等待态 ==================== */

    private Decision routePending(String t, PendingAction pending) {
        String type = pending.type() == null ? "" : pending.type();

        // 等待付款：用户说"我付好了"→ 触发查订单；说"算了"→ 取消订单
        if (PendingAction.PAY.equals(type)) {
            if (containsAny(t, PAID_CLAIM_WORDS)) {
                return new Decision(Intent.CONFIRM, null, "等付款态：声称已付款");
            }
            if (isCancel(t)) {
                return new Decision(Intent.CANCEL, null, "等付款态：取消");
            }
            return null;
        }

        // 等待用户报金额
        if (PendingAction.RECHARGE_AMOUNT.equals(type)) {
            Integer a = extractLooseAmount(t);
            if (a != null) {
                return new Decision(Intent.RECHARGE, a, "等金额态：给出金额");
            }
            if (isCancel(t)) {
                return new Decision(Intent.CANCEL, null, "等金额态：取消");
            }
            return null;
        }

        // 等待确认：确认 / 取消 / 改金额
        if (PendingAction.RECHARGE_CONFIRM.equals(type)) {
            if (isConfirm(t)) {
                return new Decision(Intent.CONFIRM, null, "等确认态：确认");
            }
            if (isCancel(t)) {
                return new Decision(Intent.CANCEL, null, "等确认态：取消");
            }
            Integer a = extractLooseAmount(t);
            if (a != null && t.length() <= 12) {
                return new Decision(Intent.RECHARGE, a, "等确认态：改金额");
            }
            return null;
        }

        return null;
    }

    /** 短答判定：必须先短，再全量匹配，避免「好，我报了哪些课」被当成确认 */
    private boolean isConfirm(String t) {
        String s = t.replaceAll("[\\s，。！!、,.~～]", "");
        if (s.isEmpty() || s.length() > 8) {
            return false;
        }
        return containsAny(s, CONFIRM_WORDS);
    }

    private boolean isCancel(String t) {
        String s = t.replaceAll("[\\s，。！!、,.~～]", "");
        if (s.isEmpty() || s.length() > 10) {
            return false;
        }
        return containsAny(s, CANCEL_WORDS);
    }

    /* ==================== 查询判定 ==================== */

    private boolean isRechargeQuery(String t) {
        boolean aboutRecharge = containsAny(t, RECHARGE_WORDS) || t.contains("充");
        return aboutRecharge && containsAny(t, RECHARGE_QUERY_WORDS);
    }

    private boolean isCourseQuery(String t) {
        boolean aboutCourse = t.contains("课") || t.contains("班");
        return aboutCourse && containsAny(t, COURSE_QUERY_WORDS);
    }

    /**
     * 知识问题判定：<b>三个条件同时成立</b>才算。
     *
     * <ol>
     *   <li>命中具体健身术语（{@link #KNOWLEDGE_TOPIC_WORDS}）——保证话题真的是健身；</li>
     *   <li>命中疑问词（{@link #QUESTION_WORDS}）——保证是"问"不是"要"；</li>
     *   <li>不含强计划诉求（{@link #PLAN_REQUEST_WORDS}）——"要一份计划"永远优先。</li>
     * </ol>
     *
     * 这样「深蹲膝盖疼怎么办」进知识库，而「我想减脂」「帮我出一份减脂计划」照旧走计划生成。
     */
    private boolean isKnowledgeQuestion(String t) {
        if (containsAny(t, PLAN_REQUEST_WORDS)) {
            return false;
        }
        return containsAny(t, KNOWLEDGE_TOPIC_WORDS) && containsAny(t, QUESTION_WORDS);
    }

    /* ==================== 金额抽取 ==================== */

    /** 强模式：明确带充值动词或货币单位 */
    public Integer extractAmount(String t) {
        Integer v = matchAmount(t, "(?:充|续|加)\\s*(\\d+(?:\\.\\d+)?)");
        if (v != null) {
            return v;
        }
        v = matchAmount(t, "(?:金额|充值)\\s*[:：是为]*\\s*(\\d+(?:\\.\\d+)?)");
        if (v != null) {
            return v;
        }
        v = matchAmount(t, "(\\d+(?:\\.\\d+)?)\\s*(?:元|块|块钱|rmb)");
        if (v != null) {
            return v;
        }
        // 中文数字：充两百 / 续一百
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:充|续|加)了?\\s*([一二两三四五六七八九十百千]+)")
                .matcher(t);
        if (m.find()) {
            int n = chineseNumber(m.group(1));
            if (n > 0) {
                return n;
            }
        }
        return null;
    }

    /** 弱模式：等待态下允许只回一个数字，例如「200」 */
    public Integer extractLooseAmount(String t) {
        Integer v = extractAmount(t);
        if (v != null) {
            return v;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(?:元|块|块钱)?\\s*$")
                .matcher(t);
        if (m.find()) {
            try {
                return (int) Math.rint(Double.parseDouble(m.group(1)));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        java.util.regex.Matcher c = java.util.regex.Pattern
                .compile("^\\s*([一二两三四五六七八九十百千]+)\\s*(?:元|块|块钱)?\\s*$")
                .matcher(t);
        if (c.find()) {
            int n = chineseNumber(c.group(1));
            return n > 0 ? n : null;
        }
        return null;
    }

    private Integer matchAmount(String t, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(t);
        if (m.find()) {
            try {
                return (int) Math.rint(Double.parseDouble(m.group(1)));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    /** 简易中文数字解析，覆盖「两百」「一百五」「二十」这类口语充值金额 */
    private int chineseNumber(String s) {
        Map<Character, Integer> digits = new LinkedHashMap<>();
        digits.put('零', 0);
        digits.put('一', 1);
        digits.put('二', 2);
        digits.put('两', 2);
        digits.put('三', 3);
        digits.put('四', 4);
        digits.put('五', 5);
        digits.put('六', 6);
        digits.put('七', 7);
        digits.put('八', 8);
        digits.put('九', 9);

        int total = 0;
        int section = 0;
        int number = 0;
        for (char c : s.toCharArray()) {
            Integer d = digits.get(c);
            if (d != null) {
                number = d;
                continue;
            }
            int unit = switch (c) {
                case '十' -> 10;
                case '百' -> 100;
                case '千' -> 1000;
                default -> 0;
            };
            if (unit == 0) {
                continue;
            }
            section += (number == 0 ? 1 : number) * unit;
            number = 0;
        }
        total += section + number;
        return total;
    }

    /* ==================== 工具 ==================== */

    /**
     * 这句话里有没有"要健身计划"的诉求。
     *
     * <p>供 {@link ChatService} 做多轮续接判断：用户上一句说"我想减脂"、下一句只回"175/80/30/男"，
     * 单独看最后一句是看不懂的——必须回头确认上文里出现过计划诉求。
     */
    public boolean mentionsPlan(String text) {
        return text != null && containsAny(text.strip().toLowerCase(), PLAN_WORDS);
    }

    private boolean containsAny(String t, String[] words) {
        for (String w : words) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这句是不是"在提问"（只看疑问词，不管话题）。
     *
     * <p>兜底层用它当知识库兜底的准入条件：规则词表没认出健身主题时，
     * 只有"像在提问"的句子才会再去知识库碰一次运气，
     * 陈述句（「你们这服务太差了」）不会被拉去检索，省得答非所问。
     */
    public boolean looksLikeQuestion(String text) {
        return containsAny(normalize(text), QUESTION_WORDS);
    }

    /** 全角转半角 + 小写，保证同一套词表能匹配各种输入法输出 */
    private String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else if (c >= 'Ａ' && c <= 'Ｚ') {
                sb.append((char) (c - 'Ａ' + 'a'));
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().trim();
    }
}
