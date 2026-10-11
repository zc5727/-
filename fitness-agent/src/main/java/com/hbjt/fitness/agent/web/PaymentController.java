package com.hbjt.fitness.agent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hbjt.fitness.agent.client.AgentBridgeClient;
import com.hbjt.fitness.agent.support.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付页专用接口。
 *
 * <p>链路设计：手机扫码打开的支付页**不直连业务系统**，而是走智能体转发。
 * 业务系统的 {@code /agentBridge/**} 不在 Nginx 代理范围内，浏览器根本访问不到，
 * 于是「谁能改钱」只有一个出口（智能体），便于日后加限流与审计。
 *
 * <p>演示口径：{@code confirm} 即"模拟支付成功"。真实接入支付网关时，
 * 这里应改由网关异步回调驱动，接口本身不用动。
 */
@RestController
@RequestMapping("/api/qa/pay")
public class PaymentController {

    private final AgentBridgeClient bridgeClient;

    public PaymentController(AgentBridgeClient bridgeClient) {
        this.bridgeClient = bridgeClient;
    }

    /** 订单状态：供支付页展示、也供电脑端轮询是否已到账 */
    @GetMapping("/{orderNo}")
    public JsonNode status(@PathVariable int orderNo) {
        return requireOrder(bridgeClient.getOrder(orderNo), orderNo);
    }

    /** 确认支付（演示为模拟支付）：置为已支付并给余额入账，重复调用安全 */
    @PostMapping("/{orderNo}/confirm")
    public JsonNode confirm(@PathVariable int orderNo) {
        return requireOrder(bridgeClient.payOrder(orderNo), orderNo);
    }

    /**
     * 订单不存在时转成真正的 404。
     *
     * <p>业务系统对"订单不存在"是<b>返回 HTTP 200、响应体里带 code=404</b>（老系统的统一写法）。
     * 若原样透传，支付页会拿到一个没有 status 字段的对象，落进"待支付"分支——
     * 一个根本不存在的订单被渲染成可支付页面，用户点下去才报错。
     * 因此在智能体这一层把它规整为符合本系统错误契约的 404，让前端能区分
     * 「订单不存在」和「订单待支付」。
     */
    private JsonNode requireOrder(JsonNode payload, int orderNo) {
        if (payload == null || payload.path("code").asInt(200) == 404 || !payload.hasNonNull("orderNo")) {
            throw new NotFoundException("订单不存在：" + orderNo);
        }
        return payload;
    }
}
