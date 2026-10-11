package com.hbjt.fitness.agent.web;

import com.hbjt.fitness.agent.config.LlmConfig;
import com.hbjt.fitness.agent.config.LlmConfigResolver;
import com.hbjt.fitness.agent.config.LlmProperties;
import com.hbjt.fitness.agent.domain.ActivityLevel;
import com.hbjt.fitness.agent.domain.Gender;
import com.hbjt.fitness.agent.domain.Goal;
import com.hbjt.fitness.agent.dto.AgentMetadata;
import com.hbjt.fitness.agent.dto.ChatRequest;
import com.hbjt.fitness.agent.dto.ChatResponse;
import com.hbjt.fitness.agent.dto.PlanRequest;
import com.hbjt.fitness.agent.dto.PlanResponse;
import com.hbjt.fitness.agent.service.ChatService;
import com.hbjt.fitness.agent.service.FitnessPlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 智能问答对外接口。
 *
 * <p><b>关于大模型配置</b>：服务端可用环境变量注入默认密钥；调用方也可以用请求头
 * {@code X-LLM-Api-Key} / {@code X-LLM-Base-Url} / {@code X-LLM-Model} 覆盖，
 * 优先级为「请求头 &gt; 服务端」。这样前端可以提供一个填写入口让使用者自己填 key，
 * 而服务端不需要保存任何密钥。任一字段不传即回落到服务端默认值。
 */
@RestController
@RequestMapping("/api/qa")
public class FitnessPlanController {

    private final FitnessPlanService planService;
    private final ChatService chatService;
    private final LlmConfigResolver llmResolver;
    private final LlmProperties llmProps;

    public FitnessPlanController(FitnessPlanService planService,
                                 ChatService chatService,
                                 LlmConfigResolver llmResolver,
                                 LlmProperties llmProps) {
        this.planService = planService;
        this.chatService = chatService;
        this.llmResolver = llmResolver;
        this.llmProps = llmProps;
    }

    /**
     * 直接生成健身计划（表单式调用，保留给接口集成方）。
     * <p>响应中的 status 只会是 OK 或 DEGRADED，两者都会带回一份可用计划。
     */
    @PostMapping("/fitness-plan")
    public PlanResponse fitnessPlan(
            @Valid @RequestBody PlanRequest request,
            @RequestHeader(name = "X-LLM-Api-Key", required = false) String apiKey,
            @RequestHeader(name = "X-LLM-Base-Url", required = false) String baseUrl,
            @RequestHeader(name = "X-LLM-Model", required = false) String model) {
        return planService.plan(request, llmResolver.resolve(apiKey, baseUrl, model));
    }

    /**
     * 对话式交互：前端提交对话历史，后端抽取槽位、缺则追问、齐则出计划。
     *
     * <p>两套状态的分工很明确：
     * <ul>
     *   <li><b>运行状态</b>（本轮在等什么）由前端回传 {@code pending}，服务端不保存；</li>
     *   <li><b>历史记录</b>落智能体自己的库，会话标识 {@code sessionKey} 在响应里回给前端，
     *       下一轮带回来即可续上。归属由 {@code token} 解析，浏览器标识只用于未登录访客。</li>
     * </ul>
     * 是否记录不影响回答：存储不可用时本轮照常返回，只是 {@code sessionKey} 为空。
     */
    @PostMapping("/chat")
    public ChatResponse chat(
            @RequestBody ChatRequest request,
            @RequestHeader(name = "X-LLM-Api-Key", required = false) String apiKey,
            @RequestHeader(name = "X-LLM-Base-Url", required = false) String baseUrl,
            @RequestHeader(name = "X-LLM-Model", required = false) String model,
            @RequestHeader(name = "token", required = false) String token,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        LlmConfig cfg = llmResolver.resolve(apiKey, baseUrl, model);
        return chatService.chat(request, cfg, token, clientId);
    }

    /**
     * 能力元数据：供前端渲染提示与范围说明，避免前后端枚举各写一份。
     * <p>{@code llmConfigured} 只表示「服务端是否配置了默认密钥」，
     * 前端若自带 key，则以此为准自行判断可用性。
     */
    @GetMapping("/metadata")
    public AgentMetadata metadata() {
        List<AgentMetadata.Option> genders = Arrays.stream(Gender.values())
                .map(g -> new AgentMetadata.Option(g.name(), g.label(), null))
                .toList();

        List<AgentMetadata.Option> goals = List.of(
                new AgentMetadata.Option(Goal.LOSE_FAT.name(), Goal.LOSE_FAT.label(), "每日约 500 kcal 热量缺口，蛋白质 2.0 g/kg"),
                new AgentMetadata.Option(Goal.GAIN_MUSCLE.name(), Goal.GAIN_MUSCLE.label(), "每日约 300 kcal 热量盈余，蛋白质 2.0 g/kg"),
                new AgentMetadata.Option(Goal.SHAPE.name(), Goal.SHAPE.label(), "每日约 200 kcal 热量缺口，力量与有氧结合"),
                new AgentMetadata.Option(Goal.MAINTAIN.name(), Goal.MAINTAIN.label(), "热量收支平衡，蛋白质 1.6 g/kg"));

        List<AgentMetadata.Option> activities = Arrays.stream(ActivityLevel.values())
                .map(a -> new AgentMetadata.Option(a.name(), a.label(),
                        "TDEE 系数 " + a.factor()))
                .toList();

        List<AgentMetadata.Option> models = List.of(
                new AgentMetadata.Option("deepseek-flash", "DeepSeek Flash", "响应快、成本低，适合日常计划生成"),
                new AgentMetadata.Option("deepseek-v4-pro", "DeepSeek V4 Pro", "推理更强，适合复杂身体条件组合"));

        return new AgentMetadata(
                genders,
                goals,
                activities,
                models,
                new AgentMetadata.Range(10, 100, "岁"),
                new AgentMetadata.Range(100, 250, "cm"),
                new AgentMetadata.Range(25, 300, "kg"),
                new AgentMetadata.Range(3, 60, "%"),
                llmProps.isConfigured(),
                llmProps.getModel(),
                llmProps.getBaseUrl());
    }
}
