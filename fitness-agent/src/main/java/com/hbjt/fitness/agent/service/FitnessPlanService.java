package com.hbjt.fitness.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbjt.fitness.agent.client.DeepSeekClient;
import com.hbjt.fitness.agent.config.LlmConfig;
import com.hbjt.fitness.agent.dto.FitnessPlan;
import com.hbjt.fitness.agent.dto.HealthMetrics;
import com.hbjt.fitness.agent.dto.MemberProfile;
import com.hbjt.fitness.agent.dto.PlanRequest;
import com.hbjt.fitness.agent.dto.PlanResponse;
import com.hbjt.fitness.agent.knowledge.KnowledgeProperties;
import com.hbjt.fitness.agent.knowledge.KnowledgeService;
import com.hbjt.fitness.agent.prompt.PromptLibrary;
import com.hbjt.fitness.agent.support.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 智能问答编排层。
 *
 * <p>调用链：解析画像 → 计算指标（证据） → 检索知识库（资料） → 调 LLM 生成计划
 * → 结构校验 → 失败则规则兜底。
 *
 * <p>核心契约：<b>永远返回一份可用计划</b>。
 * 只要 LLM 环节出现任何问题（未配置、超时、空返回、JSON 非法、计划为空），
 * 一律回退到规则引擎并标记 {@code DEGRADED}，不会把空结果当成成功抛给调用方。
 */
@Service
public class FitnessPlanService {

    private static final Logger log = LoggerFactory.getLogger(FitnessPlanService.class);

    /** 单个维度最多保留的条目数，防止模型输出过长 */
    private static final int MAX_ITEMS_PER_SECTION = 10;

    /** 计划生成时检索知识库的分类范围：计划模板 + 训练基础 + 安全 */
    private static final Set<String> PLAN_KB_CATEGORIES = Set.of("plan", "training", "safety");

    private final MemberProfileService profileService;
    private final HealthMetricsService metricsService;
    private final RuleBasedPlanBuilder ruleBuilder;
    private final DeepSeekClient llm;
    private final KnowledgeService knowledge;
    private final KnowledgeProperties knowledgeProps;
    private final ObjectMapper om = new ObjectMapper();

    public FitnessPlanService(MemberProfileService profileService,
                              HealthMetricsService metricsService,
                              RuleBasedPlanBuilder ruleBuilder,
                              DeepSeekClient llm,
                              KnowledgeService knowledge,
                              KnowledgeProperties knowledgeProps) {
        this.profileService = profileService;
        this.metricsService = metricsService;
        this.ruleBuilder = ruleBuilder;
        this.llm = llm;
        this.knowledge = knowledge;
        this.knowledgeProps = knowledgeProps;
    }

    /**
     * @param cfg 本次调用生效的大模型配置（服务端默认 + 请求级覆盖已合并）
     */
    public PlanResponse plan(PlanRequest req, LlmConfig cfg) {
        long start = System.currentTimeMillis();

        MemberProfile profile = profileService.resolve(req);
        HealthMetrics metrics = metricsService.compute(profile);

        if (!cfg.isConfigured()) {
            log.info("未配置大模型密钥，直接使用规则引擎 memberNo={}", profile.memberNo());
            return degrade(metrics, profile, "未配置大模型密钥", start);
        }

        try {
            String kbContext = knowledgeContext(metrics, profile);
            String content = llm.chatJson(cfg, PromptLibrary.PLAN_SYSTEM,
                    PromptLibrary.planUser(metrics, profile, kbContext));
            FitnessPlan plan = parsePlan(content);
            long ms = System.currentTimeMillis() - start;
            log.info("智能体生成成功 status=OK model={} prompt={} kbChars={} 耗时={}ms",
                    cfg.model(), PromptLibrary.PLAN_VERSION, kbContext.length(), ms);
            return PlanResponse.ok(metrics, plan, ms, PromptLibrary.PLAN_VERSION);

        } catch (LlmUnavailableException e) {
            log.warn("大模型不可用，降级到规则引擎：{}", e.getMessage());
            return degrade(metrics, profile, e.publicMessage(), start);

        } catch (Exception e) {
            // 兜底防线：任何未预期异常都不允许逃逸成 5xx
            log.error("智能体编排异常，降级到规则引擎", e);
            return degrade(metrics, profile, "大模型输出处理异常", start);
        }
    }

    private PlanResponse degrade(HealthMetrics metrics, MemberProfile profile, String reason, long start) {
        FitnessPlan plan = ruleBuilder.build(metrics, profile);
        long ms = System.currentTimeMillis() - start;
        return PlanResponse.degraded(metrics, plan, reason, ms);
    }

    /* ================== 知识库参考 ================== */

    /**
     * 为计划生成检索参考资料。
     *
     * <p>查询词由目标 + 身体状况拼成，而不是用会员的原话：原话可能只有"我想减脂"
     * 三个字，检索不到"减脂期训练安排""安全下限"这些真正需要的内容。
     * 这里把已算出的指标（BMI 分级、体脂分级、目标）折算成检索词，
     * 风险相关的词单独进查询，保证安全类文档能被召回。
     */
    private String knowledgeContext(HealthMetrics m, MemberProfile p) {
        if (!knowledge.isReady()) {
            return "";
        }
        String query = String.join(" ",
                m.goalLabel(),
                "训练计划 安排",
                m.bmiCategory(),
                m.bodyFatCategory(),
                m.age() >= 60 ? "长者 低冲击" : "",
                m.calorieFloorApplied() ? "减脂 安全 注意事项" : "注意事项");
        return knowledge.promptContext(query, PLAN_KB_CATEGORIES, knowledgeProps.getPromptChars());
    }

    /* ================== 输出解析 ================== */

    /**
     * 解析模型输出。任何不合格的情况都抛 {@link LlmUnavailableException} 触发降级，
     * 绝不返回「空计划 + OK 状态」这种假成功。
     */
    private FitnessPlan parsePlan(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmUnavailableException("大模型返回了空内容", "content 为空");
        }

        String json = stripCodeFence(raw.trim());
        JsonNode root;
        try {
            root = om.readTree(json);
        } catch (Exception e) {
            log.error("大模型输出不是合法 JSON，前 200 字符：{}", json.substring(0, Math.min(200, json.length())));
            throw new LlmUnavailableException("大模型输出格式异常", "JSON 解析失败: " + e.getMessage(), e);
        }

        // 容错：模型偶尔直接返回数组
        if (root.isArray()) {
            throw new LlmUnavailableException("大模型输出结构不符合约定", "期望 json 对象，实际为数组");
        }

        FitnessPlan plan = new FitnessPlan(
                textOrNull(root, "summary"),
                stringList(root.get("training")),
                stringList(root.get("diet")),
                stringList(root.get("precautions")));

        if (plan.isEmpty()) {
            throw new LlmUnavailableException("大模型返回了空计划", "summary/training/diet/precautions 均为空");
        }
        return plan;
    }

    /** 去掉可能被模型包上的 ```json ... ``` 代码块围栏 */
    private String stripCodeFence(String s) {
        if (!s.startsWith("```")) {
            return s;
        }
        int firstNewline = s.indexOf('\n');
        if (firstNewline < 0) {
            return s;
        }
        String body = s.substring(firstNewline + 1);
        int fence = body.lastIndexOf("```");
        return fence >= 0 ? body.substring(0, fence).trim() : body.trim();
    }

    private String textOrNull(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        String v = n.asText();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            String v = item.isTextual() ? item.asText() : item.toString();
            if (v != null && !v.isBlank()) {
                out.add(v.trim());
            }
            if (out.size() >= MAX_ITEMS_PER_SECTION) {
                break;
            }
        }
        return out;
    }
}
