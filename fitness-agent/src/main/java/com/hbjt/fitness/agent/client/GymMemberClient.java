package com.hbjt.fitness.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbjt.fitness.agent.config.GymProperties;
import com.hbjt.fitness.agent.support.NotFoundException;
import com.hbjt.fitness.agent.support.UpstreamException;
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
 * 读取业务系统（gym-management-system）会员档案。
 *
 * <p>这是两个系统之间<b>唯一的耦合点</b>，且是契约耦合而非数据库耦合：
 * 业务库表结构变化不会直接击穿智能体，智能体也能脱离业务系统独立启动。
 */
@Component
public class GymMemberClient {

    private static final Logger log = LoggerFactory.getLogger(GymMemberClient.class);
    private static final int LOG_LIMIT = 200;

    private final GymProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public GymMemberClient(GymProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();
    }

    /**
     * @throws NotFoundException 会员不存在
     * @throws UpstreamException 业务系统不可用 / 响应无法解析
     */
    public GymMember fetchByMemberNo(long memberNo) {
        if (!props.isEnabled()) {
            throw new UpstreamException("会员档案服务未启用",
                    "fitness-agent.gym.enabled=false");
        }

        String url = props.memberByNoUrl(memberNo);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(props.getReadTimeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 404) {
                throw new NotFoundException("会员不存在：memberNo=" + memberNo);
            }
            if (response.statusCode() != 200) {
                log.error("读取会员失败 HTTP {} url={} body={}", response.statusCode(), url, abbreviate(response.body()));
                throw new UpstreamException("会员档案服务返回异常",
                        "HTTP " + response.statusCode() + " url=" + url);
            }

            String body = response.body();
            if (body == null || body.isBlank() || "null".equals(body.trim())) {
                throw new NotFoundException("会员不存在：memberNo=" + memberNo);
            }

            GymMember member = om.readValue(body, GymMember.class);
            if (member == null || member.memberNo() == null) {
                throw new NotFoundException("会员不存在：memberNo=" + memberNo);
            }
            return member;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("读取会员异常 url={}: {}", url, e.toString());
            throw new UpstreamException("无法连接会员档案服务", e.getMessage(), e);
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "<null>";
        }
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= LOG_LIMIT ? oneLine : oneLine.substring(0, LOG_LIMIT) + "...";
    }
}
