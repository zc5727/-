package com.hbjt.fitness.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hbjt.fitness.agent.config.LlmConfig;
import com.hbjt.fitness.agent.config.LlmProperties;
import com.hbjt.fitness.agent.support.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * DeepSeek（OpenAI 兼容协议）客户端。
 *
 * <p>配置由调用方以 {@link LlmConfig} 传入——既可来自服务端环境变量，
 * 也可来自用户在前端自行填写、随请求头下发的覆盖值，客户端不关心来源。
 *
 * <p>与常见实现的四个关键差别：
 * <ol>
 *   <li>HttpClient 复用单例并设置连接超时——否则一次网络抖动会把请求线程永久挂住</li>
 *   <li>返回空内容视为失败——DeepSeek 官方文档明确 JSON 模式「偶尔会返回空内容」，
 *       若不判定，会把空计划当成成功结果下发</li>
 *   <li>上游响应体只写日志、不回传调用方——避免把供应商内部错误原文暴露给前端</li>
 *   <li>密钥只出现在 Authorization 头里，任何日志与回显都不带 key</li>
 * </ol>
 */
@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    /** 日志里截断上游响应体，避免刷屏 */
    private static final int LOG_BODY_LIMIT = 300;

    private final ObjectMapper om;
    private final HttpClient http;

    public DeepSeekClient(LlmProperties props, ObjectMapper om) {
        this.om = om;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 发起一次结构化对话，返回模型输出的 JSON 字符串。
     *
     * @param cfg          本次调用生效的配置（含密钥、地址、模型）
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @throws LlmUnavailableException 未配置 key、网络异常、非 200、空内容
     */
    public String chatJson(LlmConfig cfg, String systemPrompt, String userPrompt) {
        return chat(cfg, systemPrompt, userPrompt, true);
    }

    /**
     * 发起一次自由文本对话，返回模型输出的纯文本。
     *
     * <p>与 {@link #chatJson} 的唯一差别是不带 {@code response_format}，
     * 用于「兜底引导话术」这类只需要一句话、不需要 JSON 结构的场景。
     */
    public String chatText(LlmConfig cfg, String systemPrompt, String userPrompt) {
        return chat(cfg, systemPrompt, userPrompt, false);
    }

    private String chat(LlmConfig cfg, String systemPrompt, String userPrompt, boolean jsonMode) {
        if (!cfg.isConfigured()) {
            throw new LlmUnavailableException("未配置大模型密钥",
                    "请求未携带 key，服务端 DEEPSEEK_API_KEY 也为空");
        }

        String payload = buildPayload(cfg, systemPrompt, userPrompt, jsonMode);
        String url = cfg.chatCompletionsUrl();
        String tag = jsonMode ? "json" : "text";

        LlmUnavailableException last = null;
        int attempts = Math.max(0, cfg.maxRetries()) + 1;

        for (int i = 1; i <= attempts; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(cfg.readTimeout())
                        // 强制 HTTP/1.1：Java 默认对明文连接尝试 h2c 升级，
                        // 内网推理网关的代理层不支持，会把请求体弄丢（表现为上游 400 body=None）
                        .version(HttpClient.Version.HTTP_1_1)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + cfg.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = response.statusCode();

                if (code == 200) {
                    String content = extractContent(response.body());
                    if (content == null || content.isBlank()) {
                        // DeepSeek JSON 模式已知会出现空返回，重试一次仍有希望
                        last = new LlmUnavailableException(
                                "大模型返回了空内容",
                                "content 为空, model=" + cfg.model() + ", attempt=" + i);
                        log.warn("LLM 返回空内容 model={} attempt={}/{}", cfg.model(), i, attempts);
                        continue;
                    }
                    log.info("LLM 调用成功 mode={} {} attempt={} chars={}", tag, cfg.describe(), i, content.length());
                    return content;
                }

                String body = abbreviate(response.body());
                if (code == 429 || code >= 500) {
                    last = new LlmUnavailableException(
                            "大模型服务暂时不可用",
                            "HTTP " + code + " body=" + body + ", attempt=" + i);
                    log.warn("LLM 临时失败 HTTP {} attempt={}/{} body={}", code, i, attempts, body);
                    continue;
                }

                // 4xx：密钥、额度、参数问题，重试无意义，直接放弃
                log.error("LLM 调用被拒绝 HTTP {} {} body={}", code, cfg.describe(), body);
                throw new LlmUnavailableException(
                        "大模型调用被拒绝（HTTP " + code + "）",
                        "HTTP " + code + " body=" + body);

            } catch (IOException e) {
                last = new LlmUnavailableException(
                        "无法连接大模型服务", "IO 异常: " + e.getMessage() + ", attempt=" + i, e);
                log.warn("LLM 连接异常 attempt={}/{}: {}", i, attempts, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmUnavailableException(
                        "大模型调用被中断", "线程中断", e);
            }
        }

        throw last != null ? last
                : new LlmUnavailableException("大模型调用失败", "未知原因");
    }

    private String buildPayload(LlmConfig cfg, String systemPrompt, String userPrompt, boolean jsonMode) {
        ObjectNode body = om.createObjectNode();
        body.put("model", cfg.model());
        body.put("temperature", cfg.temperature());
        body.put("max_tokens", cfg.maxTokens());
        body.put("stream", false);
        if (jsonMode) {
            // DeepSeek 官方要求：JSON 模式必须设置 max_tokens，否则输出可能被截断成非法 JSON
            body.putObject("response_format").put("type", "json_object");
        }
        // vLLM 后端（内网平台）思考模式默认开启，会把 token 耗在 reasoning 上导致 content 为空；
        // 显式关闭。OpenAI 兼容的其他后端会忽略未知字段，不影响官方 DeepSeek。
        body.putObject("chat_template_kwargs").put("enable_thinking", false);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        try {
            return om.writeValueAsString(body);
        } catch (Exception e) {
            throw new LlmUnavailableException("构造大模型请求失败", e.getMessage(), e);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = om.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("LLM 响应缺少 choices 字段");
                return null;
            }
            String content = choices.path(0).path("message").path("content").asText(null);
            // 兜底：极少数情况下思考内容可能混入正文，剥掉 <think>...</think> 段
            if (content != null && content.contains("<think>")) {
                content = content.replaceAll("(?s)<think>.*?</think>", "").trim();
            }
            return content;
        } catch (Exception e) {
            log.error("解析 LLM 响应失败: {}", e.getMessage());
            return null;
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "<null>";
        }
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= LOG_BODY_LIMIT ? oneLine : oneLine.substring(0, LOG_BODY_LIMIT) + "...";
    }
}
