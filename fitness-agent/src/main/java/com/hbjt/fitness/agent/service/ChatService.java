package com.hbjt.fitness.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hbjt.fitness.agent.client.AgentBridgeClient;
import com.hbjt.fitness.agent.client.GymMember;
import com.hbjt.fitness.agent.client.GymMemberClient;
import com.hbjt.fitness.agent.config.LlmConfig;
import com.hbjt.fitness.agent.domain.ActivityLevel;
import com.hbjt.fitness.agent.domain.Gender;
import com.hbjt.fitness.agent.domain.Goal;
import com.hbjt.fitness.agent.domain.Intent;
import com.hbjt.fitness.agent.dto.ChatCard;
import com.hbjt.fitness.agent.dto.ChatMessage;
import com.hbjt.fitness.agent.dto.ChatRequest;
import com.hbjt.fitness.agent.dto.ChatResponse;
import com.hbjt.fitness.agent.dto.ChatStatus;
import com.hbjt.fitness.agent.dto.PendingAction;
import com.hbjt.fitness.agent.dto.PlanRequest;
import com.hbjt.fitness.agent.dto.PlanResponse;
import com.hbjt.fitness.agent.knowledge.KnowledgeHit;
import com.hbjt.fitness.agent.knowledge.KnowledgeService;
import com.hbjt.fitness.agent.security.OwnerContext;
import com.hbjt.fitness.agent.support.NotFoundException;
import com.hbjt.fitness.agent.support.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hbjt.fitness.agent.observability.TraceContext;
import com.hbjt.fitness.agent.observability.TraceRecorder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 对话式智能问答编排 —— 系统的核心。
 *
 * <p>一轮请求的处理链路：
 * <ol>
 *   <li><b>意图路由</b>：结合"上一轮在等什么"判断用户这句想干什么</li>
 *   <li><b>动作执行</b>：查询类直接读业务系统；办理类先出确认卡再落单</li>
 *   <li><b>结果成卡</b>：能摆出来的就摆出来——确认按钮、二维码、表格、回执</li>
 * </ol>
 *
 * <p>三条贯穿全局的原则：
 * <ul>
 *   <li><b>绝不猜</b>：听不懂就说听不懂并列出能力清单</li>
 *   <li><b>改数据前必确认</b>：任何花钱的动作都要用户点头，且金额原样展示</li>
 *   <li><b>无状态</b>：一轮问答只依赖本次请求带来的内容，不依赖进程内记忆；
 *       会话历史落库、会话标识由前端回传，因此多实例部署也不会串话</li>
 * </ul>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** 充值金额区间，与业务侧桥接接口保持一致 */
    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 10000;

    /**
     * "这一句是身体数据而不是诉求"的识别式。
     * 用于多轮续接：用户被追问后回的那句往往是纯数据（"175 80 30 男"），本身不含任何意图词。
     */
    private static final java.util.regex.Pattern SLOT_DATA = java.util.regex.Pattern.compile(
            "身高|体重|年龄|体脂|性别|\\d+\\s*(cm|CM|厘米|kg|KG|公斤|斤|岁)|男|女");

    private final ChatSlotExtractor extractor;
    private final IntentRouter router;
    private final FitnessPlanService planService;
    private final GymMemberClient gymMemberClient;
    private final AgentBridgeClient bridgeClient;
    private final FallbackReplyService fallback;
    private final KnowledgeService knowledge;
    private final KnowledgeAnswerService knowledgeAnswer;
    private final SessionService session;
    private final TraceRecorder recorder;
    private final PlanTaskService planTasks;

    public ChatService(ChatSlotExtractor extractor,
                       IntentRouter router,
                       FitnessPlanService planService,
                       GymMemberClient gymMemberClient,
                       AgentBridgeClient bridgeClient,
                       FallbackReplyService fallback,
                       KnowledgeService knowledge,
                       KnowledgeAnswerService knowledgeAnswer,
                       SessionService session,
                       TraceRecorder recorder,
                       PlanTaskService planTasks) {
        this.extractor = extractor;
        this.router = router;
        this.planService = planService;
        this.gymMemberClient = gymMemberClient;
        this.bridgeClient = bridgeClient;
        this.fallback = fallback;
        this.knowledge = knowledge;
        this.knowledgeAnswer = knowledgeAnswer;
        this.session = session;
        this.recorder = recorder;
        this.planTasks = planTasks;
    }

    /* ==================== 主入口 ==================== */

    /**
     * @param token    登录令牌，用于解析会话归属（可为空）
     * @param clientId 浏览器本地标识，未登录时用它区分归属（可为空）
     */
    public ChatResponse chat(ChatRequest req, LlmConfig cfg, String token, String clientId) {
        // 一次对话 = 一条 trace，内部各阶段耗时自动挂到它上面。
        // finally 里两件事都不能漏：落库 + clear()。
        // 漏掉 clear 会让复用的 Tomcat 线程把上一次的 trace 带给下一次请求，指标全串味。
        TraceContext.Trace trace = TraceContext.start("chat");
        // 根阶段：无论走大模型还是规则路径，trace 至少有一个阶段。
        // 否则澄清/欢迎这类不调模型的请求会留下一条 spans 为空的记录，
        // 瀑布图上一片空白，延迟分位还会被这些 0ms 样本拉偏。
        try (TraceContext.Span ignored = TraceContext.span("chat.total", "total")) {
            return doChat(req, cfg, token, clientId, trace);
        } finally {
            try {
                recorder.record(trace);
            } catch (RuntimeException ignore) {
                // 观测写入失败绝不影响对话
            }
            TraceContext.clear();
        }
    }

    private ChatResponse doChat(ChatRequest req, LlmConfig cfg, String token, String clientId,
                                TraceContext.Trace trace) {
        boolean llmReady = cfg.isConfigured();
        boolean memberMode = req.memberId() != null;

        List<String> userTexts = new ArrayList<>();
        for (ChatMessage m : req.safeMessages()) {
            if (m.isUser()) {
                userTexts.add(m.content() == null ? "" : m.content());
            }
        }

        if (userTexts.stream().allMatch(String::isBlank)) {
            // 没有用户发言就不开会话：否则会话列表里会多出一堆只有问候语的空壳
            trace.discard = true;   // 也没进路由，不算一次调用，不该计入调用量与延迟分位
            return welcome(memberMode, llmReady);
        }

        String lastUser = lastNonBlank(userTexts);

        // 身体数据槽位：从全部发言里重新抽取，因此多轮补充天然成立
        ChatSlotExtractor.Slots bag = new ChatSlotExtractor.Slots();
        List<String> warnings = new ArrayList<>();
        for (String t : userTexts) {
            warnings.addAll(extractor.extract(t, bag).warnings());
        }

        IntentRouter.Decision decision = router.route(lastUser, req.pending());
        Intent intent = decision.intent();

        trace.intent = intent == null ? "UNKNOWN" : intent.name();
        trace.userText = clip(lastUser, 500);

        // 多轮续接：上一句在聊计划、这一句只是在补身体数据（"175/80/30/男"），
        // 单看最后一句永远看不懂。这类情况按健身计划处理，否则用户得把"我想减脂"重说一遍。
        if (intent == Intent.UNKNOWN && isPlanFollowUp(userTexts)) {
            log.info("意图续接：上文含计划诉求且本轮补充了身体数据 → 按健身计划处理");
            intent = Intent.FITNESS_PLAN;
        }

        log.info("意图判定 intent={} reason={} amount={} pending={}",
                intent, decision.reason(), decision.amount(),
                req.pending() == null ? "-" : req.pending().type());

        ChatResponse resp = switch (intent) {
            case CONFIRM -> onConfirm(req, memberMode, llmReady);
            case CANCEL -> onCancel(req, llmReady);
            case QUERY_BALANCE -> queryBalance(req, memberMode, llmReady);
            case QUERY_INTEGRAL -> queryIntegral(req, memberMode, llmReady);
            case QUERY_RECHARGE -> queryRechargeList(req, memberMode, llmReady);
            case QUERY_COURSE -> queryCourseList(req, memberMode, llmReady);
            case QUERY_KNOWLEDGE -> answerKnowledge(cfg, lastUser, memberMode, llmReady);
            case RECHARGE -> startRecharge(req, decision.amount(), memberMode, llmReady);
            case FITNESS_PLAN -> buildPlan(req, bag, warnings, cfg, llmReady);
            case REGISTER_COURSE -> notOpen("课程报名", llmReady);
            case CHECKIN -> notOpen("签到打卡", llmReady);
            case HELP -> help(llmReady);
            default -> unknown(cfg, lastUser, memberMode, llmReady);
        };

        // 结果状态回填：降级必须可见，否则"今天没人用"和"记不下来"会混为一谈
        if (resp != null && resp.status() != null) {
            trace.status = resp.status().name();
            if (resp.status() == com.hbjt.fitness.agent.dto.ChatStatus.DEGRADED) {
                trace.degraded = true;
            }
        }
        if (!llmReady) {
            trace.degraded("LLM_NOT_CONFIGURED");
        }

        // 记住这一轮：归属由令牌/浏览器标识解析得到，会话标识缺失就新开一个
        Optional<OwnerContext> owner = session.owner(token, clientId);
        owner.ifPresent(c -> {
            trace.ownerType = c.type();
            trace.ownerId = c.id();
        });
        trace.sessionKey = req.sessionKey();
        if (resp != null && resp.plan() != null && !resp.plan().isEmpty()) {
            materializePlan(req, owner, resp.plan());
        }
        return session.attach(req, owner, resp);
    }

    /**
     * 把生成的计划展开成一周的每日任务。
     *
     * <p>这里刻意不抛异常：任务展开是增值能力，落库失败不该让已经生成好的计划
     * 连同一起丢掉。失败只记日志，计划照常返回。
     */
    private void materializePlan(ChatRequest req, Optional<OwnerContext> owner,
                                 com.hbjt.fitness.agent.dto.FitnessPlan plan) {
        try {
            String type = owner.map(OwnerContext::type).orElse("anonymous");
            String id = owner.map(OwnerContext::id).orElse("-");
            String planKey = "P" + System.currentTimeMillis();
            planTasks.materialize(planKey, req.sessionKey(), type, id, plan, LocalDate.now());
        } catch (RuntimeException e) {
            log.warn("计划任务展开失败（不影响计划返回）：{}", e.getMessage());
        }
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /* ==================== 等待态：确认 / 取消 ==================== */

    private ChatResponse onConfirm(ChatRequest req, boolean memberMode, boolean llmReady) {
        PendingAction pending = req.pending();
        if (pending == null) {
            return ChatResponse.say(ChatStatus.ASK,
                    "我这边没有待确认的事情。你可以说「充 200」发起充值，或者问我余额、积分、充值记录。",
                    Map.of(), List.of(), List.of(), null, llmReady);
        }
        if (PendingAction.PAY.equals(pending.type())) {
            return checkOrder(pending.orderNo(), llmReady);
        }
        if (PendingAction.RECHARGE_CONFIRM.equals(pending.type()) && pending.amount() != null) {
            return createOrder(req, pending.amount(), memberMode, llmReady);
        }
        return ChatResponse.say(ChatStatus.ASK, "要充多少？直接说个数字就行，比如「200」。",
                Map.of(), List.of(), List.of(), PendingAction.askAmount(), llmReady);
    }

    private ChatResponse onCancel(ChatRequest req, boolean llmReady) {
        PendingAction pending = req.pending();
        String reply;
        if (pending != null && PendingAction.PAY.equals(pending.type())) {
            reply = "好的，已取消。订单 " + pending.orderNo() + " 没有支付，不会扣你的钱。";
        } else if (pending != null) {
            reply = "好的，不充了。还有别的需要吗？";
        } else {
            reply = "好的。还有别的需要吗？";
        }
        return ChatResponse.say(ChatStatus.ASK, reply, Map.of(), List.of(), List.of(), null, llmReady);
    }

    /* ==================== 充值：两段式（先确认，再落单） ==================== */

    private ChatResponse startRecharge(ChatRequest req, Integer amount, boolean memberMode, boolean llmReady) {
        if (amount == null) {
            return ChatResponse.say(ChatStatus.ASK,
                    "好的，要充多少？直接说个数字就行，比如「200」。",
                    Map.of(), List.of(), List.of(), PendingAction.askAmount(), llmReady);
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            return ChatResponse.say(ChatStatus.ASK,
                    "单次充值金额需要在 " + MIN_AMOUNT + "-" + MAX_AMOUNT + " 元之间，请说一个在这个范围内的金额。",
                    Map.of(), List.of(), List.of(), PendingAction.askAmount(), llmReady);
        }
        if (!memberMode) {
            return ChatResponse.say(ChatStatus.ASK,
                    "充值需要知道是给哪位会员充。请用会员账号登录后再发起，或告诉我会员编号。",
                    Map.of(), List.of(), List.of(), null, llmReady);
        }

        Integer balance = safeBalance(req.memberId());
        return ChatResponse.say(ChatStatus.ASK,
                "确认一下：给当前会员充值 ¥" + amount + "，这是你的操作吗？",
                Map.of(), List.of(),
                List.of(ChatCard.confirmRecharge(amount, balance)),
                PendingAction.confirmRecharge(amount),
                llmReady);
    }

    private ChatResponse createOrder(ChatRequest req, int amount, boolean memberMode, boolean llmReady) {
        if (!memberMode) {
            return ChatResponse.say(ChatStatus.ASK, "充值需要会员身份，请用会员账号登录。",
                    Map.of(), List.of(), List.of(), null, llmReady);
        }
        int memberNo = req.memberId().intValue();
        JsonNode res;
        try {
            res = bridgeClient.createRechargeOrder(memberNo, amount);
        } catch (UpstreamException e) {
            log.warn("创建充值订单失败：{}", e.getMessage());
            return ChatResponse.say(ChatStatus.ASK, "下单没成功：" + e.publicMessage() + "。稍后再试一次吧。",
                    Map.of(), List.of(), List.of(), null, llmReady);
        }

        int code = res.path("code").asInt();
        if (code != 200) {
            return ChatResponse.say(ChatStatus.ASK,
                    "下单失败：" + res.path("message").asText("未知原因"),
                    Map.of(), List.of(), List.of(), null, llmReady);
        }

        int orderNo = res.path("orderNo").asInt();
        // 二维码内容给的是相对路径，由前端结合"局域网地址"拼成手机可扫的绝对地址
        ChatCard payCard = ChatCard.pay(orderNo, amount, "/pay/" + orderNo);

        return ChatResponse.say(ChatStatus.ASK,
                "订单已生成，扫下面这张码支付 ¥" + amount + "。付完点「我已完成支付」，或者直接跟我说一声。",
                Map.of(), List.of(), List.of(payCard),
                PendingAction.waitingPay(orderNo, amount),
                llmReady);
    }

    private ChatResponse checkOrder(Integer orderNo, boolean llmReady) {
        if (orderNo == null) {
            return ChatResponse.say(ChatStatus.ASK, "我这边没有待支付的订单。",
                    Map.of(), List.of(), List.of(), null, llmReady);
        }
        JsonNode res;
        try {
            res = bridgeClient.getOrder(orderNo);
        } catch (UpstreamException e) {
            return ChatResponse.say(ChatStatus.ASK, "查询订单状态失败：" + e.publicMessage(),
                    Map.of(), List.of(), List.of(), PendingAction.waitingPay(orderNo, 0), llmReady);
        }

        String status = res.path("status").asText();
        int amount = res.path("amount").asInt();
        Integer balance = res.path("balance").isNumber() ? res.path("balance").asInt() : null;

        if ("PAID".equals(status)) {
            return ChatResponse.say(ChatStatus.OK,
                    "到账了！充值 ¥" + amount + " 已入账，当前余额 ¥" + balance + "。",
                    Map.of(), List.of(),
                    List.of(ChatCard.receipt("充值成功", orderNo, amount, balance)),
                    null,
                    llmReady);
        }
        return ChatResponse.say(ChatStatus.ASK,
                "还没收到这笔付款的结果，订单 " + orderNo + " 仍是待支付状态。付完后跟我说一声就行。",
                Map.of(), List.of(),
                List.of(ChatCard.pay(orderNo, amount, "/pay/" + orderNo)),
                PendingAction.waitingPay(orderNo, amount),
                llmReady);
    }

    /* ==================== 查询四件套 ==================== */

    private ChatResponse queryBalance(ChatRequest req, boolean memberMode, boolean llmReady) {
        if (!memberMode) {
            return needMember("查余额", llmReady);
        }
        Integer balance = safeBalance(req.memberId());
        return ChatResponse.say(ChatStatus.OK,
                balance == null
                        ? "没能读到你的余额，稍后再试一次吧。"
                        : "你卡里还有 ¥" + balance + "。",
                Map.of(), List.of(), List.of(), null, llmReady);
    }

    private ChatResponse queryIntegral(ChatRequest req, boolean memberMode, boolean llmReady) {
        if (!memberMode) {
            return needMember("查积分", llmReady);
        }
        try {
            JsonNode node = bridgeClient.getIntegral(req.memberId().intValue());
            int integral = node.isNumber() ? node.asInt() : (int) node.asDouble();
            return ChatResponse.say(ChatStatus.OK, "你目前有 " + integral + " 积分。",
                    Map.of(), List.of(), List.of(), null, llmReady);
        } catch (UpstreamException e) {
            return ChatResponse.say(ChatStatus.ASK, "读取积分失败：" + e.publicMessage(),
                    Map.of(), List.of(), List.of(), null, llmReady);
        }
    }

    private ChatResponse queryRechargeList(ChatRequest req, boolean memberMode, boolean llmReady) {
        if (!memberMode) {
            return needMember("查充值记录", llmReady);
        }
        try {
            JsonNode arr = bridgeClient.getRechargeList(req.memberId().intValue());
            List<List<String>> rows = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    int status = n.path("rechargeStatus").asInt();
                    rows.add(List.of(
                            String.valueOf(n.path("rechargeNo").asInt()),
                            n.path("rechargeDate").asText("-"),
                            "¥" + money(n.path("rechargeMoney")),
                            status == 1 ? "已支付" : "待支付"));
                }
            }
            String reply = rows.isEmpty() ? "你还没有充值记录。" : "你一共有 " + rows.size() + " 笔充值记录：";
            return ChatResponse.say(ChatStatus.OK, reply, Map.of(), List.of(),
                    List.of(ChatCard.list("充值记录", "按时间倒序",
                            List.of("订单号", "日期", "金额", "状态"), rows, "暂无记录")),
                    null, llmReady);
        } catch (UpstreamException e) {
            return ChatResponse.say(ChatStatus.ASK, "读取充值记录失败：" + e.publicMessage(),
                    Map.of(), List.of(), List.of(), null, llmReady);
        }
    }

    private ChatResponse queryCourseList(ChatRequest req, boolean memberMode, boolean llmReady) {
        if (!memberMode) {
            return needMember("查我的课程", llmReady);
        }
        try {
            JsonNode arr = bridgeClient.getCourseList(req.memberId().intValue());
            List<List<String>> rows = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    rows.add(List.of(
                            n.path("courseName").asText("-"),
                            n.path("courseDuration").asText("-") + " 节",
                            n.path("employeeName").asText("-"),
                            n.path("courseTime").asText("-")));
                }
            }
            String reply = rows.isEmpty() ? "你还没有报名任何课程。" : "你已报名 " + rows.size() + " 门课程：";
            return ChatResponse.say(ChatStatus.OK, reply, Map.of(), List.of(),
                    List.of(ChatCard.list("我的课程", null,
                            List.of("课程", "课时", "教练", "开课日期"), rows, "暂无报名")),
                    null, llmReady);
        } catch (UpstreamException e) {
            return ChatResponse.say(ChatStatus.ASK, "读取课程记录失败：" + e.publicMessage(),
                    Map.of(), List.of(), List.of(), null, llmReady);
        }
    }

    /* ==================== 健身计划（原有能力） ==================== */

    private ChatResponse buildPlan(ChatRequest req, ChatSlotExtractor.Slots bag,
                                   List<String> warnings, LlmConfig cfg, boolean llmReady) {
        boolean memberMode = req.memberId() != null;

        GymMember member = null;
        if (memberMode) {
            try {
                member = gymMemberClient.fetchByMemberNo(req.memberId());
            } catch (NotFoundException e) {
                return ChatResponse.say(ChatStatus.ASK,
                        "没有找到编号 " + req.memberId() + " 的会员档案。请确认会员编号，"
                                + "或者直接把性别、年龄、身高、体重告诉我。",
                        Map.of(), List.of("会员档案"), List.of(), null, llmReady);
            } catch (UpstreamException e) {
                return ChatResponse.say(ChatStatus.ASK,
                        "会员档案服务暂时不可用，你可以先把性别、年龄、身高、体重告诉我。",
                        Map.of(), List.of("会员档案"), List.of(), null, llmReady);
            }
        }

        Gender gender = bag.gender != null ? bag.gender
                : (member != null ? Gender.fromGymCode(member.genderCode()) : null);
        Integer age = bag.age != null ? bag.age : (member != null ? member.age() : null);
        Double height = bag.height != null ? bag.height : (member != null ? member.height() : null);
        Double weight = bag.weight != null ? bag.weight : (member != null ? member.weight() : null);
        ActivityLevel activity = bag.activityLevel != null ? bag.activityLevel : ActivityLevel.MODERATE;
        String name = firstNonBlank(req.name(), member != null ? member.name() : null);

        Map<String, String> slotView = slotsView(bag, gender, age, height, weight, bag.goal);

        List<String> missing = new ArrayList<>();
        if (gender == null) missing.add("性别");
        if (age == null) missing.add("年龄");
        if (height == null) missing.add("身高");
        if (weight == null) missing.add("体重");
        if (bag.goal == null) missing.add("健身目标");

        if (!missing.isEmpty()) {
            return ChatResponse.say(ChatStatus.ASK, askReply(missing, warnings, memberMode),
                    slotView, missing, List.of(), null, llmReady);
        }

        PlanRequest planRequest = new PlanRequest(
                req.memberId(), name, gender, age, height, weight, bag.bodyFat, activity, bag.goal);
        PlanResponse planResponse = planService.plan(planRequest, cfg);

        String reply = planResponse.status() == com.hbjt.fitness.agent.domain.PlanStatus.OK
                ? "数据齐了，指标我已经算好，计划如下 👇"
                : "大模型这会儿不可用，我用规则引擎给你生成了一份等效计划 👇";

        return ChatResponse.withPlan(reply, slotView, planResponse, llmReady);
    }

    /* ==================== 知识库问答 ==================== */

    /**
     * 知识问答：主题词+疑问词命中后走这里。
     *
     * <p>检索没命中时不硬答，直接转兜底话术——"答不上来就承认"和"改数据前必确认"是同一种取向：
     * 宁可少答一句，也不要编一句。
     */
    private ChatResponse answerKnowledge(LlmConfig cfg, String question, boolean memberMode, boolean llmReady) {
        List<KnowledgeHit> hits = knowledge.search(question);
        Optional<KnowledgeAnswerService.Answer> answer =
                knowledgeAnswer.answer(cfg, question, memberMode, hits);
        return answer.map(a -> knowledgeResponse(a, llmReady))
                .orElseGet(() -> guideOrFixed(cfg, question, memberMode, llmReady));
    }

    /** 把知识问答结果摆成"回答 + 来源卡"。来源必须摆出来，否则用户无从判断这是不是模型随口说的。 */
    private ChatResponse knowledgeResponse(KnowledgeAnswerService.Answer a, boolean llmReady) {
        List<ChatCard> cards = a.hits().isEmpty() ? List.of()
                : List.of(ChatCard.source(KnowledgeService.citations(a.hits())));
        log.info("知识问答命中 {} 篇 fromLlm={} 来源={}", a.hits().size(), a.fromLlm(),
                a.hits().isEmpty() ? "-" : a.hits().get(0).citation());
        return ChatResponse.sayWithPrompt(a.reply(), cards, llmReady, a.promptVersion());
    }

    /* ==================== 兜底与说明 ==================== */

    private ChatResponse welcome(boolean memberMode, boolean llmReady) {
        String reply = memberMode
                ? "你好，我是健身助手。你现在可以直接跟我说话，我能查账、也能帮你充值。"
                : "你好，我是健身助手。直接说你想干什么就行，不用点菜单。";
        return ChatResponse.say(ChatStatus.ASK, reply, Map.of(), List.of(),
                List.of(capabilityCard()), null, llmReady);
    }

    private ChatResponse help(boolean llmReady) {
        return ChatResponse.say(ChatStatus.ASK, "我能办这些事：", Map.of(), List.of(),
                List.of(capabilityCard()), null, llmReady);
    }

    /**
     * 意图未命中。给两次机会，最后才认输：
     * <ol>
     *   <li><b>第二道机会——知识库</b>：规则词表没认出主题，但用户确实在提问、
     *       且知识库搜得出东西（如「泡沫轴怎么用」），就据资料回答；
     *       非健身语境（如「今天天气怎么样」）检索分不到阈值，自然落到下一层。</li>
     *   <li><b>大模型引导话术</b>：受硬约束 + 后置校验，越界即丢弃；</li>
     *   <li><b>固定话术</b>：未配置密钥或调用失败时用，保证用户永远有回应。</li>
     * </ol>
     */
    private ChatResponse unknown(LlmConfig cfg, String userText, boolean memberMode, boolean llmReady) {
        if (router.looksLikeQuestion(userText)) {
            List<KnowledgeHit> hits = knowledge.search(userText);
            Optional<KnowledgeAnswerService.Answer> answer =
                    knowledgeAnswer.answer(cfg, userText, memberMode, hits);
            if (answer.isPresent()) {
                return knowledgeResponse(answer.get(), llmReady);
            }
        }
        return guideOrFixed(cfg, userText, memberMode, llmReady);
    }

    /** 引导话术（LLM 生成，受约束），失败则回退固定话术 */
    private ChatResponse guideOrFixed(LlmConfig cfg, String userText, boolean memberMode, boolean llmReady) {
        String fixed = "这句我没听懂——我不猜你的意思，免得办错事。可以换个说法，或者从下面挑一个：";
        ChatResponse fallbackResp = ChatResponse.say(ChatStatus.ASK, fixed, Map.of(), List.of(),
                List.of(capabilityCard()), null, llmReady);

        return fallback.guide(cfg, userText, memberMode)
                .map(text -> ChatResponse.sayWithPrompt(text, List.of(capabilityCard()),
                        llmReady, com.hbjt.fitness.agent.prompt.PromptLibrary.FALLBACK_VERSION))
                .orElse(fallbackResp);
    }

    private ChatResponse notOpen(String what, boolean llmReady) {
        return ChatResponse.say(ChatStatus.ASK,
                what + "这个功能还没开通，目前只做了下面这几件事：",
                Map.of(), List.of(), List.of(capabilityCard()), null, llmReady);
    }

    private ChatResponse needMember(String action, boolean llmReady) {
        return ChatResponse.say(ChatStatus.ASK,
                action + "需要知道是哪个会员。请用会员账号登录后再问我。",
                Map.of(), List.of(), List.of(), null, llmReady);
    }

    /** 能力清单与提示词里的白名单同源（{@link PromptLibrary#CAPABILITIES}），避免两处漂移 */
    private ChatCard capabilityCard() {
        return ChatCard.notice("我可以帮你做这些", com.hbjt.fitness.agent.prompt.PromptLibrary.CAPABILITIES);
    }

    /* ==================== 工具 ==================== */

    private Integer safeBalance(Long memberNo) {
        if (memberNo == null) {
            return null;
        }
        try {
            JsonNode node = bridgeClient.getBalance(memberNo.intValue());
            return node.isNumber() ? node.asInt() : (int) node.asDouble();
        } catch (Exception e) {
            log.warn("读取余额失败 memberNo={}: {}", memberNo, e.getMessage());
            return null;
        }
    }

    private String money(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "0";
        }
        double v = node.asDouble();
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private String lastNonBlank(List<String> texts) {
        for (int i = texts.size() - 1; i >= 0; i--) {
            if (texts.get(i) != null && !texts.get(i).isBlank()) {
                return texts.get(i);
            }
        }
        return "";
    }

    /**
     * 判断这一轮是不是"上一轮计划追问的续答"。
     *
     * <p>触发条件两条同时满足：
     * <ol>
     *   <li>本轮（最后一句）是身体数据，而不是别的问题；</li>
     *   <li>上文里出现过计划诉求（"我想减脂"之类）。</li>
     * </ol>
     * 两条缺一不可——只看第一条会把随便报身高的人当成要计划；只看第二条会让"我想减脂"之后
     * 的每一句话都重复出计划。
     */
    private boolean isPlanFollowUp(List<String> userTexts) {
        if (userTexts.size() < 2) {
            return false;
        }
        String last = lastNonBlank(userTexts);
        if (!SLOT_DATA.matcher(last).find()) {
            return false;
        }
        for (int i = 0; i < userTexts.size() - 1; i++) {
            if (router.mentionsPlan(userTexts.get(i))) {
                return true;
            }
        }
        return false;
    }

    private String askReply(List<String> missing, List<String> warnings, boolean memberMode) {
        StringBuilder sb = new StringBuilder();
        if (!warnings.isEmpty()) {
            sb.append(String.join("；", warnings)).append("。\n\n");
        }
        if (missing.size() == 1) {
            sb.append(switch (missing.get(0)) {
                case "性别" -> "还差一步：你是男生还是女生？";
                case "年龄" -> "年龄是多少？（10-100 岁）";
                case "身高" -> "身高大概多少？例如 175cm。";
                case "体重" -> "体重是多少？例如 80kg。";
                case "健身目标" -> "最后确认下目标：你想减脂、增肌、塑形还是维持？";
                default -> "再补充一下" + missing.get(0) + "吧。";
            });
            return sb.toString();
        }
        sb.append("我还需要这些信息才能把指标算准：").append(String.join("、", missing)).append("。\n");
        sb.append(memberMode
                ? "性别、年龄、身高、体重我会从会员档案读取，你也可以直接说；目标请告诉我一个。"
                : "可以一句话说清，例如「男 28岁 175cm 80kg 想减脂」。");
        return sb.toString();
    }

    private Map<String, String> slotsView(ChatSlotExtractor.Slots bag,
                                          Gender gender, Integer age,
                                          Double height, Double weight, Goal goal) {
        Map<String, String> view = new LinkedHashMap<>();
        if (gender != null) {
            view.put("性别", gender.label());
        }
        if (age != null) {
            view.put("年龄", age + " 岁");
        }
        if (height != null) {
            view.put("身高", trim(height) + " cm");
        }
        if (weight != null) {
            view.put("体重", trim(weight) + " kg");
        }
        if (bag != null && bag.bodyFat != null) {
            view.put("体脂率", trim(bag.bodyFat) + " %");
        }
        if (goal != null) {
            view.put("目标", goal.label());
        }
        if (bag != null && bag.activityLevel != null) {
            view.put("活动水平", bag.activityLevel.label());
        }
        return view;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return (b != null && !b.isBlank()) ? b.trim() : null;
    }

    private String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
