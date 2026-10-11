package com.hbjt.fitness.agent.support;

/**
 * 入参不合法 → HTTP 400。用于「缺必填字段」「数值超范围」等可自解释的错误。
 */
public class BadRequestException extends AgentException {

    public BadRequestException(String publicMessage) {
        super(publicMessage, publicMessage);
    }

    public BadRequestException(String publicMessage, String detail) {
        super(publicMessage, detail);
    }
}
