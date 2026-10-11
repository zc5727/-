package com.hbjt.fitness.agent.support;

/**
 * 业务异常基类。
 * <p>约定：{@code publicMessage} 会返回给调用方，{@code detail} 只写日志。
 * 这样可以做到「错误可解释」又不泄漏内部实现（如上游响应体、密钥相关的报错原文）。
 */
public class AgentException extends RuntimeException {

    private final String publicMessage;

    public AgentException(String publicMessage, String detail) {
        super(detail == null ? publicMessage : detail);
        this.publicMessage = publicMessage;
    }

    public AgentException(String publicMessage, String detail, Throwable cause) {
        super(detail == null ? publicMessage : detail, cause);
        this.publicMessage = publicMessage;
    }

    public String publicMessage() {
        return publicMessage;
    }
}
