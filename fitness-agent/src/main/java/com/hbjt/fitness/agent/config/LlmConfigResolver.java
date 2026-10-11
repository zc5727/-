package com.hbjt.fitness.agent.config;

import org.springframework.stereotype.Component;

/**
 * 把「服务端默认配置」与「请求级覆盖」合成为一次调用真正生效的 {@link LlmConfig}。
 *
 * <p>覆盖来源是请求头（{@code X-LLM-Api-Key} / {@code X-LLM-Base-Url} / {@code X-LLM-Model}），
 * 由前端"API 设置"面板填写后带上。优先级：请求头 &gt; 服务端环境变量。
 * 任一字段缺省则回落到服务端默认值，因此用户可以只填 key、只换模型，或完全不填。
 *
 * <p>为什么不做服务端存储：密钥一旦落库就多了一份泄露面。请求级透传让智能体保持无状态，
 * 密钥只存在于调用方的浏览器里。
 */
@Component
public class LlmConfigResolver {

    private final LlmProperties props;

    public LlmConfigResolver(LlmProperties props) {
        this.props = props;
    }

    /**
     * @param apiKey 请求头覆盖的密钥，可为 null / 空
     * @param baseUrl 请求头覆盖的地址，可为 null / 空
     * @param model 请求头覆盖的模型，可为 null / 空
     */
    public LlmConfig resolve(String apiKey, String baseUrl, String model) {
        return new LlmConfig(
                firstNonBlank(baseUrl, props.getBaseUrl()),
                firstNonBlank(apiKey, props.getApiKey()),
                firstNonBlank(model, props.getModel()),
                props.getTemperature(),
                props.getMaxTokens(),
                props.getConnectTimeout(),
                props.getReadTimeout(),
                props.getMaxRetries());
    }

    /** 服务端默认配置（不带任何请求级覆盖）。 */
    public LlmConfig serverDefault() {
        return resolve(null, null, null);
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback;
    }
}
