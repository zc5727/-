package com.hbjt.fitness.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbjt.fitness.agent.db.ChatStore;
import com.hbjt.fitness.agent.db.ChatTurn;
import com.hbjt.fitness.agent.db.ChatSessionSummary;
import com.hbjt.fitness.agent.db.DbProperties;
import com.hbjt.fitness.agent.db.StoredMessage;
import com.hbjt.fitness.agent.dto.ChatMessage;
import com.hbjt.fitness.agent.dto.ChatRequest;
import com.hbjt.fitness.agent.dto.ChatResponse;
import com.hbjt.fitness.agent.security.OwnerContext;
import com.hbjt.fitness.agent.security.OwnerResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话编排：把「一次问答」与「一条会话记录」接起来。
 *
 * <p>它做三件事，且只做这三件：
 * <ol>
 *   <li><b>认人</b>：把请求凭据交给 {@link OwnerResolver} 换成可验证的归属；</li>
 *   <li><b>认会话</b>：前端带了 sessionKey 且确实属于该归属就沿用，否则新开一个。
 *       关键在于"确实属于"这一步——带了别人的 key 不会被沿用，只会另开新会话，
 *       拿到别人的 key 也读不到别人的历史。</li>
 *   <li><b>落库</b>：把本轮的用户发言与助手回复追加进去。</li>
 * </ol>
 *
 * <p>整体取向是<b>失败即降级</b>：任何一步出错都只是"这一轮不记录"，
 * 绝不因为历史存储的问题让用户问不出话。会话历史是增值能力，不是可用性单点。
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final OwnerResolver ownerResolver;
    private final ChatStore store;
    private final DbProperties props;
    private final ObjectMapper om;

    public SessionService(OwnerResolver ownerResolver, ChatStore store, DbProperties props, ObjectMapper om) {
        this.ownerResolver = ownerResolver;
        this.store = store;
        this.props = props;
        this.om = om;
    }

    /* ==================== 归属 ==================== */

    /** 解析归属；两者都不可用时返回空，表示"本次不记录"（而不是落到公共桶里） */
    public Optional<OwnerContext> owner(String token, String clientId) {
        Optional<OwnerContext> ctx = ownerResolver.resolve(token, clientId);
        ctx.ifPresent(c -> log.debug("本轮归属 {}", c.describe()));
        return ctx;
    }

    /* ==================== 写入：把本轮结果挂到会话上 ==================== */

    /**
     * 落库并给响应盖上 sessionKey。
     *
     * <p>新会话会把前端带来的历史一并补录（这样即便前端不维护 key，历史也不会丢），
     * 已有会话则只追加本轮增量，避免同一句话被重复写入。
     *
     * @return 盖好 sessionKey 的响应；存储不可用时原样返回
     */
    public ChatResponse attach(ChatRequest req, Optional<OwnerContext> owner, ChatResponse resp) {
        if (owner.isEmpty() || resp == null || !store.isAvailable()) {
            return resp;
        }
        OwnerContext o = owner.get();
        try {
            String given = req.sessionKey();
            boolean reuse = given != null && !given.isBlank() && store.owned(given, o.type(), o.id());
            List<ChatMessage> history = req.safeMessages();
            String key = reuse ? given
                    : store.createSession(o.type(), o.id(), o.memberNo(), firstUserText(history)).orElse(null);
            if (key == null) {
                return resp;
            }

            List<ChatTurn> turns = new ArrayList<>();
            if (!reuse) {
                for (ChatMessage m : history) {
                    if (m == null || m.content() == null || m.content().isBlank()) {
                        continue;
                    }
                    turns.add(m.isUser()
                            ? ChatTurn.user(m.content(), m.content())
                            : ChatTurn.assistant(m.content(), null, null, null, null));
                }
            } else {
                ChatMessage tail = history.isEmpty() ? null : history.get(history.size() - 1);
                if (tail != null && tail.isUser() && tail.content() != null && !tail.content().isBlank()) {
                    turns.add(ChatTurn.user(tail.content(), tail.content()));
                }
            }
            turns.add(ChatTurn.assistant(resp.reply(), payload(resp), pending(resp),
                    resp.promptVersion(), resp.status() == null ? null : resp.status().name()));

            int written = store.appendTurn(key, o.type(), o.id(), turns);
            if (written == 0) {
                log.warn("本轮未写入会话（可能归属校验未通过或存储降级）key={}", key);
            }
            return resp.withSession(key);
        } catch (Exception e) {
            // 落库失败不影响本次回答：用户该看到的已经生成了
            log.warn("会话落库异常，本轮不记录：{}", e.getMessage());
            return resp;
        }
    }

    /* ==================== 读取：会话列表 / 历史消息 ==================== */

    public Map<String, Object> overview(Optional<OwnerContext> owner) {
        if (owner.isEmpty()) {
            return Map.of("available", store.isAvailable(), "sessions", List.of(),
                    "reason", store.isAvailable() ? "缺少归属凭据" : String.valueOf(store.unavailableReason()));
        }
        OwnerContext o = owner.get();
        List<ChatSessionSummary> sessions = store.listSessions(o.type(), o.id(), props.getHistoryLimit());
        return Map.of(
                "available", store.isAvailable(),
                "ownerType", o.type(),
                "verified", o.verified(),
                "sessions", sessions);
    }

    public Optional<Map<String, Object>> messages(Optional<OwnerContext> owner, String sessionKey) {
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        OwnerContext o = owner.get();
        Optional<ChatSessionSummary> session = store.findSession(sessionKey, o.type(), o.id());
        if (session.isEmpty()) {
            return Optional.empty();
        }
        List<StoredMessage> msgs = store.listMessages(sessionKey, o.type(), o.id(), props.getHistoryLimit());
        return Optional.of(Map.of("session", session.get(), "messages", msgs));
    }

    public Optional<Map<String, Object>> create(Optional<OwnerContext> owner, String title) {
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        OwnerContext o = owner.get();
        return store.createSession(o.type(), o.id(), o.memberNo(), title)
                .flatMap(key -> store.findSession(key, o.type(), o.id()))
                .map(s -> Map.of("session", s));
    }

    public boolean rename(Optional<OwnerContext> owner, String sessionKey, String title) {
        return owner.filter(o -> store.rename(sessionKey, o.type(), o.id(), title)).isPresent();
    }

    public boolean delete(Optional<OwnerContext> owner, String sessionKey) {
        return owner.filter(o -> store.delete(sessionKey, o.type(), o.id())).isPresent();
    }

    public Map<String, Object> storageStats() {
        return store.stats();
    }

    /* ==================== 工具 ==================== */

    private String firstUserText(List<ChatMessage> history) {
        for (ChatMessage m : history) {
            if (m != null && m.isUser() && m.content() != null && !m.content().isBlank()) {
                return m.content();
            }
        }
        return null;
    }

    /** 助手完整响应快照：恢复界面时据此把卡片、计划原样重建出来 */
    private String payload(ChatResponse resp) {
        try {
            return om.writeValueAsString(resp);
        } catch (Exception e) {
            return null;
        }
    }

    /** 待办快照：不存的话，恢复会话后「好」「我付好了」这类短答会失去意义 */
    private String pending(ChatResponse resp) {
        if (resp.pending() == null) {
            return null;
        }
        try {
            return om.writeValueAsString(resp.pending());
        } catch (Exception e) {
            return null;
        }
    }
}
