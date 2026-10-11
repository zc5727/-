package com.hbjt.fitness.agent.dto;

/**
 * 上一轮留下的待办动作，由前端原样回传。
 *
 * <p>这是让"短答有意义"的关键：服务端不存会话，但把"我现在在等什么"交给前端保管并回传。
 * 于是用户下一句说「好」「200」「我付好了」时，服务端知道该把它解释成
 * 「确认」「金额」「已付款」，而不是当成孤立的一句话去猜。
 *
 * @param type    待办类型，见下方常量
 * @param amount  待确认/待支付的金额
 * @param orderNo 待支付的订单号
 */
public record PendingAction(String type, Integer amount, Integer orderNo) {

    /** 已知意图是充值、但还没听到金额 */
    public static final String RECHARGE_AMOUNT = "RECHARGE_AMOUNT";

    /** 金额已知，等用户点头 */
    public static final String RECHARGE_CONFIRM = "RECHARGE_CONFIRM";

    /** 订单已创建，等用户付款 */
    public static final String PAY = "PAY";

    public static PendingAction askAmount() {
        return new PendingAction(RECHARGE_AMOUNT, null, null);
    }

    public static PendingAction confirmRecharge(int amount) {
        return new PendingAction(RECHARGE_CONFIRM, amount, null);
    }

    public static PendingAction waitingPay(int orderNo, int amount) {
        return new PendingAction(PAY, amount, orderNo);
    }
}
