package com.hbjt.fitness.agent.db;

/**
 * 会话列表项（不含消息正文，带最后一条预览）。
 *
 * @param sessionKey   会话标识，前端持有并回传
 * @param title        标题，默认取首句用户发言
 * @param ownerType    member / admin / guest
 * @param memberNo     会员场景下的会员编号
 * @param messageCount 消息条数
 * @param preview      最后一条消息的摘要，列表里做预览
 * @param createdAt    创建时间
 * @param updatedAt    最近活跃时间
 */
public record ChatSessionSummary(
        String sessionKey,
        String title,
        String ownerType,
        Long memberNo,
        int messageCount,
        String preview,
        String createdAt,
        String updatedAt
) {
}
