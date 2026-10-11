package com.hbjt.fitness.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbjt.fitness.agent.config.GymProperties;
import com.hbjt.fitness.agent.support.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * 业务系统「操作与查询」客户端。
 *
 * <p>与 {@link GymMemberClient}（只读会员档案）并列，这里负责两类事：
 * <ol>
 *   <li><b>查询</b>：余额 / 积分 / 充值记录 / 我的课程 —— 复用业务系统现有接口</li>
 *   <li><b>办理</b>：创建充值订单 / 查订单 / 确认支付 —— 走业务系统的
 *       {@code /agentBridge/**} 内部接口（该前缀不在 Nginx 代理范围内，浏览器无法直达）</li>
 * </ol>
 *
 * <p>所有返回值统一为 {@link JsonNode}，由上层决定如何组织成卡片，
 * 避免为每个接口写一个 DTO。
 */
@Component
public class AgentBridgeClient {

    private static final Logger log = LoggerFactory.getLogger(AgentBridgeClient.class);
    private static final int LOG_LIMIT = 240;

    private final GymProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public AgentBridgeClient(GymProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();
    }

    /* ==================== 查询类 ==================== */

    public JsonNode getBalance(int memberNo) {
        return get("/getMemberChange?memberNo=" + memberNo);
    }

    public JsonNode getIntegral(int memberNo) {
        return get("/getMemberIntegral?memberNo=" + memberNo);
    }

    public JsonNode getRechargeList(int memberNo) {
        return get("/getRechargeByMemberNo?memberNo=" + memberNo);
    }

    public JsonNode getCourseList(int memberNo) {
        return get("/getRegisterByMemberNo?memberNo=" + memberNo);
    }

    /* ==================== 办理类 ==================== */

    public JsonNode createRechargeOrder(int memberNo, int amount) {
        return post("/agentBridge/recharge/order?memberNo=" + memberNo + "&amount=" + amount);
    }

    public JsonNode getOrder(int orderNo) {
        return get("/agentBridge/recharge/order/" + orderNo);
    }

    public JsonNode payOrder(int orderNo) {
        return post("/agentBridge/recharge/order/" + orderNo + "/pay");
    }

    /* ==================== 身份 ==================== */

    /**
     * 解析登录令牌，取回「这条令牌代表谁」。
     *
     * <p>智能体的会话数据按归属隔离，而归属必须来自可验证的来源：
     * 让调用方在请求体里自称"我是 7 号会员"是可以被冒用的。
     * 这里把令牌交给签发方去解释，返回结果直接当作事实使用。
     *
     * @return 形如 {@code {"code":200,"role":"member","memberNo":7}}；
     *         令牌无效时 {@code code=401}
     */
    public JsonNode identity(String token) {
        return getWithToken("/agentBridge/identity", token);
    }

    /* ==================== HTTP ==================== */

    private JsonNode get(String path) {
        return exchange(HttpRequest.newBuilder(URI.create(url(path))).GET());
    }

    /** 带 token 头的 GET（身份解析用），且允许非 200 结果返回给调用方判断 */
    private JsonNode getWithToken(String path, String token) {
        if (!props.isEnabled()) {
            throw new UpstreamException("业务系统未启用", "fitness-agent.gym.enabled=false");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                    .timeout(props.getReadTimeout())
                    .header("Accept", "application/json")
                    .header("token", token == null ? "" : token)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new UpstreamException("业务系统返回空内容", "url=" + request.uri());
            }
            return om.readTree(body);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new UpstreamException("无法连接业务系统", e.getMessage(), e);
        }
    }

    private JsonNode post(String path) {
        return exchange(HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("")));
    }

    private JsonNode exchange(HttpRequest.Builder builder) {
        if (!props.isEnabled()) {
            throw new UpstreamException("业务系统未启用", "fitness-agent.gym.enabled=false");
        }
        try {
            HttpRequest request = builder
                    .timeout(props.getReadTimeout())
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("业务系统返回异常 HTTP {} url={} body={}",
                        response.statusCode(), request.uri(), abbreviate(response.body()));
                throw new UpstreamException("业务系统返回异常",
                        "HTTP " + response.statusCode() + " url=" + request.uri());
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new UpstreamException("业务系统返回空内容", "url=" + request.uri());
            }
            return om.readTree(body);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("调用业务系统失败: {}", e.toString());
            throw new UpstreamException("无法连接业务系统", e.getMessage(), e);
        }
    }

    private String url(String path) {
        String base = props.getBaseUrl() == null ? "" : props.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "<null>";
        }
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= LOG_LIMIT ? oneLine : oneLine.substring(0, LOG_LIMIT) + "...";
    }

    /** 预留：需要 URL 编码时使用 */
    @SuppressWarnings("unused")
    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
