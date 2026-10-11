package com.hbjt.fitness.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置。生产下前端由 Nginx 同源代理，无需跨域；
 * 这里主要为本地开发直连后端时留口子。
 */
@ConfigurationProperties(prefix = "fitness-agent.cors")
public class CorsProperties {

    private String allowedOrigins = "";

    public List<String> originList() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
