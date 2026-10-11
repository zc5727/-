package com.gym.service;

import com.gym.entity.Recharge;
import com.gym.mapper.AgentBridgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对话式充值闭环的业务侧实现。
 *
 * <p>补上原系统缺失的一环：<b>充值要真的把余额加上去</b>。
 * 原 {@code RechargeService} 只写流水不动余额，导致「充值成功但余额没变」，
 * 这里用一条 {@code memberChange = memberChange + delta} 把账做平。
 *
 * <p>幂等性：支付状态用「带条件的 UPDATE」（仅当仍为待支付时才改）来推进，
 * 因此重复回调、重复点击都不会重复入账。
 *
 * <p>金额口径：会员余额字段是整数（元），因此这里只接受整数元，
 * 避免出现 129.5 这种和余额字段类型冲突的金额。
 */
@Service
public class AgentBridgeService {

    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 10000;

    @Autowired
    private AgentBridgeMapper bridgeMapper;

    /** 创建待支付充值订单。 */
    public Map<String, Object> createRechargeOrder(int memberNo, double amount) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (amount != Math.rint(amount)) {
            return fail(result, 400, "目前只支持整数元充值，请改成整数金额");
        }
        int amt = (int) amount;
        if (amt < MIN_AMOUNT || amt > MAX_AMOUNT) {
            return fail(result, 400, "单次充值金额需在 " + MIN_AMOUNT + "-" + MAX_AMOUNT + " 元之间");
        }
        if (bridgeMapper.countMember(memberNo) <= 0) {
            return fail(result, 404, "会员不存在：memberNo=" + memberNo);
        }

        Recharge order = new Recharge();
        order.setMemberNo(memberNo);
        order.setRechargeMoney((double) amt);
        order.setRechargeMethod("在线充值");
        order.setRechargeStatus(0);
        bridgeMapper.insertPendingRecharge(order);

        result.put("code", 200);
        result.put("message", "订单已创建，等待支付");
        result.put("orderNo", order.getRechargeNo());
        result.put("amount", amt);
        result.put("memberNo", memberNo);
        result.put("status", "PENDING");
        result.put("balance", bridgeMapper.getMemberChange(memberNo));
        return result;
    }

    /** 查询订单状态，供前端轮询「手机上是否已付」。 */
    public Map<String, Object> getOrder(int orderNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        Recharge order = bridgeMapper.getRechargeById(orderNo);
        if (order == null) {
            return fail(result, 404, "订单不存在：" + orderNo);
        }
        Integer status = order.getRechargeStatus();
        result.put("code", 200);
        result.put("orderNo", orderNo);
        result.put("memberNo", order.getMemberNo());
        result.put("amount", order.getRechargeMoney() == null ? null : order.getRechargeMoney().intValue());
        result.put("status", status != null && status == 1 ? "PAID" : (status != null && status == 2 ? "CANCELLED" : "PENDING"));
        result.put("balance", bridgeMapper.getMemberChange(order.getMemberNo()));
        return result;
    }

    /**
     * 确认支付：置为已支付并给余额入账。
     * <p>重复调用安全——只有第一次会把钱加上去。
     */
    public Map<String, Object> payOrder(int orderNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        Recharge order = bridgeMapper.getRechargeById(orderNo);
        if (order == null) {
            return fail(result, 404, "订单不存在：" + orderNo);
        }

        int affected = bridgeMapper.markRechargePaid(orderNo);
        boolean firstPay = affected > 0;

        if (firstPay) {
            int delta = order.getRechargeMoney() == null ? 0 : order.getRechargeMoney().intValue();
            bridgeMapper.addMemberChange(order.getMemberNo(), delta);
        }

        result.put("code", 200);
        result.put("message", firstPay ? "支付成功，余额已到账" : "该订单此前已完成支付");
        result.put("orderNo", orderNo);
        result.put("memberNo", order.getMemberNo());
        result.put("amount", order.getRechargeMoney() == null ? null : order.getRechargeMoney().intValue());
        result.put("status", "PAID");
        result.put("firstPay", firstPay);
        result.put("balance", bridgeMapper.getMemberChange(order.getMemberNo()));
        return result;
    }

    private Map<String, Object> fail(Map<String, Object> result, int code, String message) {
        result.put("code", code);
        result.put("message", message);
        return result;
    }
}
