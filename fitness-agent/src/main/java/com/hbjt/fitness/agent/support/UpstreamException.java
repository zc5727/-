package com.hbjt.fitness.agent.support;

/**
 * 上游依赖（业务系统）不可用 → HTTP 502。
 */
public class UpstreamException extends AgentException {

    public UpstreamException(String publicMessage, String detail) {
        super(publicMessage, detail);
    }

    public UpstreamException(String publicMessage, String detail, Throwable cause) {
        super(publicMessage, detail, cause);
    }
}
