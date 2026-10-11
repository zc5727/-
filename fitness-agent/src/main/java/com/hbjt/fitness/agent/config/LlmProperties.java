package com.hbjt.fitness.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * LLM 接入配置（OpenAI 兼容协议）。
 * <p>默认指向 DeepSeek：base_url {@code https://api.deepseek.com}，模型 {@code deepseek-flash}。
 * 切换到通义 compatible-mode / 本地 vLLM / Ollama 只需覆盖 base-url / model。
 */
@Validated
@ConfigurationProperties(prefix = "fitness-agent.llm")
public class LlmProperties {

    private String baseUrl = "https://api.deepseek.com";

    /** 由环境变量 DEEPSEEK_API_KEY 注入；为空时走规则引擎降级 */
    private String apiKey = "";

    private String model = "deepseek-flash";

    private double temperature = 0.3;

    /** DeepSeek JSON 模式官方要求：必须设置，否则 JSON 可能被截断 */
    private int maxTokens = 1500;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(45);

    /** 仅在 5xx / 429 / 网络异常时重试的次数 */
    private int maxRetries = 1;

    /**
     * 拼接 chat/completions 地址，容忍 baseUrl 结尾是否带斜杠。
     */
    public String chatCompletionsUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
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

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
