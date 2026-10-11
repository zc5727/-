package com.hbjt.fitness.agent.dto;

/**
 * 对话中的一条消息。
 *
 * @param role    user / assistant（未知值一律按 user 处理）
 * @param content 文本内容
 */
public record ChatMessage(String role, String content) {

    public boolean isUser() {
        return role == null || !"assistant".equalsIgnoreCase(role.trim());
    }
}
