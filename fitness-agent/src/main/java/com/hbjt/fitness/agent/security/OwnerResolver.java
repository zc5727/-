package com.hbjt.fitness.agent.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hbjt.fitness.agent.client.AgentBridgeClient;
import com.hbjt.fitness.agent.support.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 归属解析：把「请求带来的凭据」翻译成「这条会话属于谁」。
 *
 * <p>顺序与优先级：
 * <ol>
 *   <li>请求头带 token → 交给业务系统解析出身份（会员编号／管理员账号）。
 *       结果缓存 60 秒：令牌校验是一次内部 HTTP 调用，每条消息都做没必要；
 *       但缓存必须有 TTL，否则"令牌被吊销"要等到重启才生效。</li>
 *   <li>没有 token（或令牌无效）→ 退化为访客，用浏览器本地标识区分。
 *       访客不是账号级隔离，因此 {@link OwnerContext#verified()} 为 false，
 *       上层与日志都能看出这一区别。</li>
 *   <li>两者都没有 → 不落库（返回空）。宁可不存，也不要落到一个"公共桶"里，
 *       那样反而制造出"所有人共享历史"的假象。</li>
 * </ol>
 *
 * <p>缓存 key 用令牌的 SHA-256 摘要，不直接存原文——令牌等价于凭据，
 * 没必要在堆里摊开摆着。
 */
@Component
public class OwnerResolver {

    private static final Logger log = LoggerFactory.getLogger(OwnerResolver.class);

    private static final long TTL_MS = 60_000L;
    private static final int CACHE_MAX = 500;
    private static final int CLIENT_ID_MAX = 64;

    private final AgentBridgeClient bridge;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public OwnerResolver(AgentBridgeClient bridge) {
        this.bridge = bridge;
    }

    /**
     * @param token    登录令牌（可为空）
     * @param clientId 浏览器本地标识（可为空）
     * @return 归属；两者都不可用时返回空，表示"本次不记录会话"
     */
    public Optional<OwnerContext> resolve(String token, String clientId) {
        if (token != null && !token.isBlank()) {
            OwnerContext ctx = verify(token);
            if (ctx != null) {
                return Optional.of(ctx);
            }
            log.warn("令牌未能通过身份解析，本次按未验证身份处理");
        }
        String clean = sanitize(clientId);
        if (clean != null) {
            return Optional.of(OwnerContext.guest(clean));
        }
        return Optional.empty();
    }

    private OwnerContext verify(String token) {
        String key = digest(token);
        Entry cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAt > now) {
            return cached.ctx;
        }

        OwnerContext ctx = null;
        try {
            JsonNode res = bridge.identity(token);
            if (res.path("code").asInt() == 200) {
                String role = res.path("role").asText("");
                if (OwnerContext.MEMBER.equals(role) && res.path("memberNo").isNumber()) {
                    ctx = OwnerContext.member(res.path("memberNo").asLong(), null);
                } else if (OwnerContext.ADMIN.equals(role)) {
                    String account = res.path("account").asText("");
                    if (!account.isBlank()) {
                        ctx = OwnerContext.admin(account);
                    }
                }
            }
        } catch (UpstreamException e) {
            // 业务系统不可用时不缓存失败：下一次请求应当重试，而不是被缓存判死
            log.warn("解析令牌失败（业务系统不可用）：{}", e.publicMessage());
            return null;
        }

        if (cache.size() >= CACHE_MAX) {
            cache.clear();
        }
        cache.put(key, new Entry(ctx, now + TTL_MS));
        return ctx;
    }

    /** 只保留安全字符，避免把任意字符串写进库（也避免日志与展示层被注入） */
    static String sanitize(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        String s = clientId.strip().replaceAll("[^A-Za-z0-9_-]", "");
        if (s.isEmpty()) {
            return null;
        }
        return s.length() <= CLIENT_ID_MAX ? s : s.substring(0, CLIENT_ID_MAX);
    }

    private static String digest(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", out[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(token.hashCode());
        }
    }

    private record Entry(OwnerContext ctx, long expireAt) {
    }
}
