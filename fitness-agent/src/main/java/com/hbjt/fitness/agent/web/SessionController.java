package com.hbjt.fitness.agent.web;

import com.hbjt.fitness.agent.security.OwnerContext;
import com.hbjt.fitness.agent.service.SessionService;
import com.hbjt.fitness.agent.support.BadRequestException;
import com.hbjt.fitness.agent.support.NotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * 会话管理接口：列表、新建、历史回放、改名、删除。
 *
 * <p><b>所有接口都只认归属，不认 sessionKey</b>。带了别人的 sessionKey，
 * 结果不是"读到别人的历史"，而是统一的"查无此会话"——归属校验写在数据访问层的
 * WHERE 条件里，这里拿不到任何越权的可能。
 *
 * <p>归属来源与对话接口一致：{@code token} 解析出的会员/管理员身份，
 * 未登录时用 {@code X-Client-Id}（浏览器本地标识）。两者都没有则默认拒绝。
 */
@RestController
@RequestMapping("/api/qa/sessions")
public class SessionController {

    private final SessionService session;

    public SessionController(SessionService session) {
        this.session = session;
    }

    /** 会话列表（含最后一条预览），按最近活跃倒序 */
    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        return session.overview(owner(token, clientId));
    }

    /** 新建空会话（用于"点一下就要一个新会话"的场景，不产生任何问答记录） */
    @PostMapping
    public Map<String, Object> create(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        String title = body == null ? null : String.valueOf(body.getOrDefault("title", ""));
        return session.create(owner(token, clientId), title)
                .orElseThrow(() -> new BadRequestException("无法创建会话：请在登录状态或提供客户端标识后重试"));
    }

    /** 历史回放：会话信息 + 全部消息（助手消息带完整响应快照，可原样重建卡片与计划） */
    @GetMapping("/{sessionKey}/messages")
    public Map<String, Object> messages(
            @PathVariable String sessionKey,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        return session.messages(owner(token, clientId), sessionKey)
                .orElseThrow(() -> new NotFoundException("找不到这个会话，或它不属于当前身份"));
    }

    /** 改名 */
    @PostMapping("/{sessionKey}/rename")
    public Map<String, Object> rename(
            @PathVariable String sessionKey,
            @RequestBody Map<String, Object> body,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        String title = body == null ? null : String.valueOf(body.getOrDefault("title", ""));
        if (title == null || title.isBlank()) {
            throw new BadRequestException("标题不能为空");
        }
        boolean ok = session.rename(owner(token, clientId), sessionKey, title);
        if (!ok) {
            throw new NotFoundException("找不到这个会话，或它不属于当前身份");
        }
        return Map.of("ok", true);
    }

    /** 删除会话及其全部消息 */
    @DeleteMapping("/{sessionKey}")
    public Map<String, Object> delete(
            @PathVariable String sessionKey,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        boolean ok = session.delete(owner(token, clientId), sessionKey);
        if (!ok) {
            throw new NotFoundException("找不到这个会话，或它不属于当前身份");
        }
        return Map.of("ok", true);
    }

    private Optional<OwnerContext> owner(String token, String clientId) {
        return session.owner(token, clientId);
    }

    /** 留个只读的存储概览入口，排查"历史到底存没存进去"时不用进容器 */
    @GetMapping("/_storage")
    public Map<String, Object> storage() {
        return session.storageStats();
    }
}
