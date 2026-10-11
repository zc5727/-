package com.hbjt.fitness.agent.support;

/**
 * 资源不存在 → HTTP 404。例如按 memberId 查询时业务系统里没有该会员。
 */
public class NotFoundException extends AgentException {

    public NotFoundException(String publicMessage) {
        super(publicMessage, publicMessage);
    }

    public NotFoundException(String publicMessage, String detail) {
        super(publicMessage, detail);
    }
}
