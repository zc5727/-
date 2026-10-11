package com.hbjt.fitness.agent.dto;

import java.util.List;
import java.util.Map;

/**
 * 富交互卡片。
 *
 * <p>对话式交互之所以不"呆板"，靠的就是这些卡片：Agent 不是回一段文字让用户自己去理解，
 * 而是直接把可操作的东西摆出来——确认按钮、支付二维码、表格、回执。
 * 一句话：<b>文字负责说，卡片负责办</b>。
 *
 * <p>各类型约定：
 * <ul>
 *   <li>{@code CONFIRM} —— data: amount / balance；actions: confirm / cancel</li>
 *   <li>{@code PAY} —— data: orderNo / amount / qrPath；actions: paid / open / cancel</li>
 *   <li>{@code LIST} —— data: columns[] / rows[][] / empty</li>
 *   <li>{@code RECEIPT} —— data: orderNo / amount / balance</li>
 *   <li>{@code NOTICE} —— data: items[]（能力清单之类）</li>
 * </ul>
 */
public record ChatCard(
        String type,
        String title,
        String subtitle,
        Map<String, Object> data,
        List<Action> actions
) {

    /** 卡片上的按钮。key 由前端回传，作为下一条用户消息的「种子」。 */
    public record Action(String key, String label, String style) {
    }

    public static final String CONFIRM = "CONFIRM";
    public static final String PAY = "PAY";
    public static final String LIST = "LIST";
    public static final String RECEIPT = "RECEIPT";
    public static final String NOTICE = "NOTICE";
    public static final String SOURCE = "SOURCE";

    public static ChatCard of(String type, String title, String subtitle,
                              Map<String, Object> data, List<Action> actions) {
        return new ChatCard(type, title, subtitle, data, actions);
    }

    /** 确认卡：金额必须原样展示，用户点头才继续 */
    public static ChatCard confirmRecharge(int amount, Integer balance) {
        return of(CONFIRM, "确认充值", "确认后生成支付二维码",
                Map.of("amount", amount, "balance", balance == null ? 0 : balance),
                List.of(new Action("confirm", "确认充值 " + amount + " 元", "primary"),
                        new Action("cancel", "取消", "default")));
    }

    /**
     * 支付卡：{@code qrPath} 是相对路径，由前端结合「局域网地址」拼成手机可扫的绝对地址。
     * <p>第三个动作 {@code open} 是给演示兜底的：没带手机时可直接在本机打开支付页。
     */
    public static ChatCard pay(int orderNo, int amount, String qrPath) {
        return of(PAY, "扫码支付", "订单号 " + orderNo + " · " + amount + " 元",
                Map.of("orderNo", orderNo, "amount", amount, "qrPath", qrPath),
                List.of(new Action("paid", "我已完成支付", "primary"),
                        new Action("open", "在本机打开支付页", "default"),
                        new Action("cancel", "取消订单", "default")));
    }

    public static ChatCard receipt(String title, int orderNo, int amount, Integer balance) {
        return of(RECEIPT, title, "订单号 " + orderNo,
                Map.of("orderNo", orderNo, "amount", amount, "balance", balance == null ? 0 : balance),
                List.of());
    }

    public static ChatCard list(String title, String subtitle,
                                List<String> columns, List<List<String>> rows, String empty) {
        return of(LIST, title, subtitle,
                Map.of("columns", columns, "rows", rows, "empty", empty == null ? "" : empty),
                List.of());
    }

    public static ChatCard notice(String title, List<String> items) {
        return of(NOTICE, title, null, Map.of("items", items), List.of());
    }

    /**
     * 来源卡：知识库问答的引用出处。
     *
     * <p>为什么要把来源单独摆成一张卡，而不是塞在正文里：
     * 用户看到"《徒手深蹲》· 常见错误"这种带出处的答复，才知道这不是模型随口说的；
     * 排查时也能一眼看出它到底检索到了什么。
     */
    public static ChatCard source(List<String> citations) {
        return of(SOURCE, "参考来源", "来自健身知识库", Map.of("items", citations), List.of());
    }
}
