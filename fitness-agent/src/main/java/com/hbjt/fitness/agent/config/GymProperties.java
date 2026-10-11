package com.hbjt.fitness.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 业务系统接入配置。
 * <p>智能体不直连业务库，只通过 REST 契约读取会员档案——
 * 这样业务库表结构变更不会击穿智能体，智能体也能脱离业务系统单独部署。
 */
@ConfigurationProperties(prefix = "fitness-agent.gym")
public class GymProperties {

    private String baseUrl = "http://gym-backend:9090";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(8);

    private boolean enabled = true;

    public String memberByNoUrl(long memberNo) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/getMemberByMemberNo?memberNo=" + memberNo;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
