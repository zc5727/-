package com.hbjt.fitness.agent.support;

/**
 * LLM 不可用或输出不合格。
 * <p>抛出即触发降级：编排层捕获后改用规则引擎，返回 {@code DEGRADED} 而不是失败。
 * 覆盖场景：未配置 key、连接超时、HTTP 非 200、返回空内容、JSON 解析失败、计划结构为空。
 */
public class LlmUnavailableException extends AgentException {

    public LlmUnavailableException(String publicMessage, String detail) {
        super(publicMessage, detail);
    }

    public LlmUnavailableException(String publicMessage, String detail, Throwable cause) {
        super(publicMessage, detail, cause);
    }
}
