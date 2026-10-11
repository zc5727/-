package com.hbjt.fitness.agent.db;

/**
 * 落库用的一轮消息。
 *
 * @param role          user / assistant
 * @param content       展示文本（前端气泡里看到的）
 * @param apiContent    参与语义的文本：按钮点出来的动作回的是种子词（"确认"），
 *                      与展示文案（"确认充值 200 元"）分开存，恢复后语义才一致
 * @param payloadJson   助手消息的完整响应快照（含卡片、计划），恢复界面用
 * @param pendingJson   待办快照，恢复后"好""我付好了"这类短答才有意义
 * @param promptVersion 本条回复使用的提示词版本
 * @param status        OK / ASK / DEGRADED
 */
public record ChatTurn(
        String role,
        String content,
        String apiContent,
        String payloadJson,
        String pendingJson,
        String promptVersion,
        String status
) {
    public static ChatTurn user(String content, String apiContent) {
        return new ChatTurn("user", content, apiContent, null, null, null, null);
    }

    public static ChatTurn assistant(String content, String payloadJson, String pendingJson,
                                     String promptVersion, String status) {
        return new ChatTurn("assistant", content, content, payloadJson, pendingJson, promptVersion, status);
    }
}
