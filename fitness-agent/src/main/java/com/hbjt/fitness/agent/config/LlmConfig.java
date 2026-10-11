package com.hbjt.fitness.agent.config;

import java.time.Duration;

/**
 * 一次调用真正生效的大模型配置（不可变值对象）。
 *
 * <p>由 {@link LlmConfigResolver} 把「服务端默认配置」与「请求级覆盖」合成。
 * 这样 {@code DeepSeekClient} 不再关心配置从哪来，只按传入的配置发请求，
 * 于是"用户在前端自己填 key"与"运维用环境变量注入 key"走的是同一条路径。
 *
 * <p>安全约束：本对象只在使用瞬间存在，绝不写入日志、不落库、不回显给调用方。
 */
public record LlmConfig(
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens,
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetries
) {

    /** 是否具备可用密钥。为空表示只能走规则引擎降级。 */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 拼接 chat/completions 地址，容忍 baseUrl 结尾带不带斜杠。
     */
    public String chatCompletionsUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    /** 仅用于日志与回显：不含密钥。 */
    public String describe() {
        return model + "@" + (baseUrl == null || baseUrl.isBlank() ? "-" : baseUrl);
    }
}
