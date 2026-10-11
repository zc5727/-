package com.hbjt.fitness.agent.dto;

import java.util.List;
import java.util.Map;

/**
 * 对话回合的响应。
 *
 * <p>{@code slots} 是「已从对话中听懂的身体数据」的可读回显（如 {@code {"身高":"175 cm"}}），
 * 前端直接渲染成小标签。{@code cards} 是这一轮要摆出来的可交互内容（确认卡、二维码、列表、回执）。
 * {@code pending} 需要前端原样保存并在下一轮回传，短答才有意义。
 */
public record ChatResponse(
        ChatStatus status,
        String reply,
        Map<String, String> slots,
        List<String> missing,
        List<ChatCard> cards,
        PendingAction pending,
        HealthMetrics metrics,
        FitnessPlan plan,
        String degradedReason,
        boolean llmConfigured,
        /** 本回合生效的提示词版本（出计划或 LLM 兜底时非空），便于线上归因 */
        String promptVersion,
        /** 本回合所属会话标识；存储不可用或未产生新内容时为空 */
        String sessionKey
) {

    /** 只说话，不办事（追问、查询答复、能力清单都用它）。 */
    public static ChatResponse say(ChatStatus status, String reply,
                                   Map<String, String> slots, List<String> missing,
                                   List<ChatCard> cards, PendingAction pending,
                                   boolean llmConfigured) {
        return new ChatResponse(status, reply, slots, missing, cards, pending,
                null, null, null, llmConfigured, null, null);
    }

    /** 出计划：status 由计划响应决定（OK / DEGRADED）。 */
    public static ChatResponse withPlan(String reply, Map<String, String> slots,
                                        PlanResponse plan, boolean llmConfigured) {
        ChatStatus status = plan.status() == com.hbjt.fitness.agent.domain.PlanStatus.OK
                ? ChatStatus.OK : ChatStatus.DEGRADED;
        return new ChatResponse(status, reply, slots, List.of(), List.of(), null,
                plan.metrics(), plan.plan(), plan.degradedReason(), llmConfigured,
                plan.promptVersion(), null);
    }

    /** 只说话，且带回本条回复所用的提示词版本（LLM 兜底话术走这条）。 */
    public static ChatResponse sayWithPrompt(String reply, List<ChatCard> cards,
                                             boolean llmConfigured, String promptVersion) {
        return new ChatResponse(ChatStatus.ASK, reply, Map.of(), List.of(), cards, null,
                null, null, null, llmConfigured, promptVersion, null);
    }

    /**
     * 挂上会话标识。
     *
     * <p>不把会话设计成"服务端记住的上下文"，而是每轮把 key 回给前端、前端再带回来——
     * 服务端依旧可以无状态地处理任何一次请求；数据库里存的是历史，不是运行状态。
     */
    public ChatResponse withSession(String key) {
        return new ChatResponse(status, reply, slots, missing, cards, pending, metrics, plan,
                degradedReason, llmConfigured, promptVersion, key);
    }
}
