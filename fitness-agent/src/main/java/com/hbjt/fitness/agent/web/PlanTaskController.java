package com.hbjt.fitness.agent.web;

import com.hbjt.fitness.agent.security.OwnerContext;
import com.hbjt.fitness.agent.security.OwnerResolver;
import com.hbjt.fitness.agent.service.PlanTaskService;
import com.hbjt.fitness.agent.support.BadRequestException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 计划执行闭环接口。
 *
 * <p>归属解析与会话、对话接口完全一致（优先令牌、其次浏览器标识、都没有则拒绝），
 * 因此一个会员只能看到和操作自己的任务。
 */
@RestController
@RequestMapping("/api/plan")
public class PlanTaskController {

    private final PlanTaskService tasks;
    private final OwnerResolver resolver;

    public PlanTaskController(PlanTaskService tasks, OwnerResolver resolver) {
        this.tasks = tasks;
        this.resolver = resolver;
    }

    /** 我的训练任务（默认近 7 天，含今天）。 */
    @GetMapping("/tasks")
    public Map<String, Object> list(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        Optional<OwnerContext> owner = resolver.resolve(token, clientId);
        Map<String, Object> res = new LinkedHashMap<>();
        if (owner.isEmpty()) {
            res.put("available", false);
            res.put("reason", "未识别归属：请登录后重试");
            return res;
        }
        LocalDate f = parseDate(from, LocalDate.now().minusDays(6));
        LocalDate t = parseDate(to, LocalDate.now().plusDays(6));
        res.put("available", true);
        res.put("items", tasks.list(owner.get().type(), owner.get().id(), f, t));
        return res;
    }

    /**
     * 任务打卡。
     *
     * @param action DONE（完成）/ SKIP（跳过）/ UNDO（撤销）
     */
    @PostMapping("/tasks/{id}/checkin")
    public Map<String, Object> checkIn(
            @PathVariable("id") long id,
            @RequestParam(value = "action", defaultValue = "DONE") String action,
            @RequestParam(value = "actualValue", required = false) String actualValue,
            @RequestParam(value = "note", required = false) String note,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        Optional<OwnerContext> owner = resolver.resolve(token, clientId);
        Map<String, Object> res = new LinkedHashMap<>();
        if (owner.isEmpty()) {
            res.put("available", false);
            res.put("reason", "未识别归属：请登录后重试");
            return res;
        }
        OwnerContext ctx = owner.get();
        int affected = tasks.checkIn(id, ctx.type(), ctx.id(), action, actualValue, note);
        res.put("available", true);
        res.put("affected", affected);
        res.put("ok", affected > 0);
        if (affected > 0) {
            res.put("adherence", tasks.adherence(ctx.type(), ctx.id(), 30));
        }
        return res;
    }

    /** 近 N 天依从率。 */
    @GetMapping("/adherence")
    public Map<String, Object> adherence(
            @RequestParam(value = "days", defaultValue = "30") int days,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        Optional<OwnerContext> owner = resolver.resolve(token, clientId);
        Map<String, Object> res = new LinkedHashMap<>();
        if (owner.isEmpty()) {
            res.put("available", false);
            res.put("reason", "未识别归属：请登录后重试");
            return res;
        }
        OwnerContext ctx = owner.get();
        return tasks.adherence(ctx.type(), ctx.id(), days);
    }

    /**
     * 解析日期参数：空值或格式错误都回退到默认值。
     *
     * <p>前端可能传来空串（用户清空了筛选框），也可能传错格式。
     * 这两种情况都按"不筛选"处理，而不是抛 400——日期只是过滤条件，
     * 不值得因为它让整个查询失败。
     */
    private static LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
