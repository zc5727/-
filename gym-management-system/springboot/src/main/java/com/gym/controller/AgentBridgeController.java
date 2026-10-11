package com.gym.controller;

import com.gym.service.AgentBridgeService;
import com.gym.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能体桥接接口（内部接口，仅供 fitness-agent 调用）。
 *
 * <p>注意：本组接口前缀是 {@code /agentBridge}，不在 Nginx 的 {@code /api/} 代理范围内，
 * 因此浏览器无法直接访问，只有容器网络里的智能体能调。对外一律由智能体做门面，
 * 这样「谁能改钱」这件事只有一个入口，便于审计与限流。
 */
@RestController
@RequestMapping("/agentBridge")
public class AgentBridgeController {

    @Autowired
    private AgentBridgeService bridgeService;

    /** 创建待支付充值订单 */
    @PostMapping("/recharge/order")
    public Map<String, Object> createOrder(@RequestParam int memberNo,
                                           @RequestParam double amount) {
        return bridgeService.createRechargeOrder(memberNo, amount);
    }

    /** 查询订单状态（前端轮询用） */
    @GetMapping("/recharge/order/{orderNo}")
    public Map<String, Object> getOrder(@PathVariable int orderNo) {
        return bridgeService.getOrder(orderNo);
    }

    /** 确认支付：置为已支付并给余额入账 */
    @PostMapping("/recharge/order/{orderNo}/pay")
    public Map<String, Object> payOrder(@PathVariable int orderNo) {
        return bridgeService.payOrder(orderNo);
    }

    /**
     * 令牌身份解析：由签发方回答"这条令牌代表谁"。
     *
     * <p>智能体的会话数据要按归属隔离，归属必须来自可验证的来源。
     * 让调用方在请求里自称"我是 7 号会员"是很容易被冒用的，
     * 因此身份统一由这里解析令牌得出，智能体只把它当作事实使用。
     *
     * @param token 登录时下发的 JWT（放在 {@code token} 请求头）
     * @return code=200 时携带 role / account / memberNo；令牌无效返回 code=401
     */
    @GetMapping("/identity")
    public Map<String, Object> identity(@RequestHeader(value = "token", required = false) String token) {
        Map<String, Object> res = new HashMap<>();
        Claims claims = JwtUtil.parse(token);
        if (claims == null) {
            res.put("code", 401);
            res.put("message", "令牌无效或已过期");
            return res;
        }
        res.put("code", 200);
        res.put("role", claims.get("role", String.class));
        res.put("account", claims.get("account", String.class));
        Object memberNo = claims.get("memberNo");
        res.put("memberNo", memberNo == null ? null : Integer.valueOf(String.valueOf(memberNo)));
        res.put("phone", claims.get("phone", String.class));
        return res;
    }
}
