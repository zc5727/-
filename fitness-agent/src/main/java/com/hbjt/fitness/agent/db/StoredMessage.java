package com.hbjt.fitness.agent.db;

/**
 * 从库里读出来的一条消息。
 *
 * @param seq           会话内序号，天然有序，不依赖时间戳（同一秒内的消息也能排对）
 * @param payloadJson   助手消息的响应快照；前端据此原样重建卡片与计划
 */
public record StoredMessage(
        long id,
        int seq,
        String role,
        String content,
        String apiContent,
        String payloadJson,
        String pendingJson,
        String promptVersion,
        String status,
        String createdAt
) {
    public boolean isUser() {
        return "user".equalsIgnoreCase(role);
    }
}
