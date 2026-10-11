package com.hbjt.fitness.agent.dto;

import java.util.List;

/**
 * 对话请求。
 *
 * <p>设计要点：**服务端不保存「运行状态」**，会话标识每轮由前端回传——
 * 于是处理任意一次请求都不依赖进程内记忆，多实例部署也不会串话。
 * 但"不保存运行状态"不等于"不保存历史"：历史落在智能体自己的库里，
 * 属于可查、可恢复的持久化数据，与内存里的会话上下文是两回事。
 *
 * @param messages   本轮对话历史（含本轮用户发言），按时间正序
 * @param memberId   会员端场景：既是权限边界，也是查询与办理的目标会员
 * @param name       称呼，可为空
 * @param pending    上一轮留下的待办动作，可为空
 * @param sessionKey 会话标识；为空表示"这一轮新开一个会话"
 */
public record ChatRequest(
        List<ChatMessage> messages,
        Long memberId,
        String name,
        PendingAction pending,
        String sessionKey
) {
    public List<ChatMessage> safeMessages() {
        return messages == null ? List.of() : messages;
    }
}
