package com.hbjt.fitness.agent.web;

import com.hbjt.fitness.agent.knowledge.KnowledgeFeedbackService;
import com.hbjt.fitness.agent.security.OwnerContext;
import com.hbjt.fitness.agent.security.OwnerResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 知识回答反馈接口。
 *
 * <p>与「点赞」这类装饰性按钮的差别：这里的反馈会<b>真实改变检索结果</b>——
 * 记入 {@code knowledge_doc_weight} 后，该文档在下一次检索中的排序权重会变。
 * 所以请求里必须带上 {@code docId} 与 {@code query}，否则这条反馈无法定位到
 * 它要评价的到底是哪一次命中，也就无从调整。
 */
@RestController
@RequestMapping("/api/knowledge/feedback")
public class KnowledgeFeedbackController {

    private final KnowledgeFeedbackService feedback;
    private final OwnerResolver resolver;

    public KnowledgeFeedbackController(KnowledgeFeedbackService feedback, OwnerResolver resolver) {
        this.feedback = feedback;
        this.resolver = resolver;
    }

    /**
     * 提交反馈。
     *
     * <p>请求体字段：{@code traceId}、{@code query}、{@code docId}（必填）、
     * {@code docTitle}、{@code score}、{@code vote}（UP/DOWN，默认 UP）、{@code comment}。
     */
    @PostMapping
    public Map<String, Object> submit(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        Map<String, Object> res = new LinkedHashMap<>();
        Map<String, Object> b = body == null ? Map.of() : body;

        String docId = str(b.get("docId"));
        if (docId == null || docId.isBlank()) {
            res.put("ok", false);
            res.put("reason", "缺少 docId：无法定位被评价的文档");
            return res;
        }

        String voteRaw = str(b.get("vote"));
        boolean up = !"DOWN".equals(voteRaw == null ? "" : voteRaw.trim().toUpperCase(Locale.ROOT));

        OwnerContext ctx = resolver.resolve(token, clientId).orElse(null);
        String ownerType = ctx == null ? "anonymous" : ctx.type();
        String ownerId = ctx == null ? (clientId == null ? "-" : clientId) : ctx.id();

        Double score = dbl(b.get("score"));
        Double weight = feedback.vote(str(b.get("traceId")), str(b.get("sessionKey")),
                ownerType, ownerId, str(b.get("query")), docId,
                str(b.get("docTitle")), score, up, str(b.get("comment")));

        res.put("ok", weight != null);
        res.put("docId", docId);
        res.put("vote", up ? "UP" : "DOWN");
        res.put("weight", weight);
        return res;
    }

    /** 各文档的反馈统计。 */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", feedback.summary());
        return res;
    }

    /** 取值并转字符串；null 原样返回，交给调用方判断必填性。 */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 取值并转小数；无法解析时返回 null，不填 0 以免污染统计。 */
    private static Double dbl(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
