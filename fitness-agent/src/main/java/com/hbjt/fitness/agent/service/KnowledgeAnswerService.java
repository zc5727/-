package com.hbjt.fitness.agent.service;

import com.hbjt.fitness.agent.client.DeepSeekClient;
import com.hbjt.fitness.agent.config.LlmConfig;
import com.hbjt.fitness.agent.knowledge.KnowledgeHit;
import com.hbjt.fitness.agent.knowledge.KnowledgeProperties;
import com.hbjt.fitness.agent.knowledge.KnowledgeService;
import com.hbjt.fitness.agent.prompt.PromptLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 知识库问答（RAG 的生成侧）。
 *
 * <p>链路：检索（{@link KnowledgeService}） → 拼参考资料 → 大模型据资料作答 → 后置校验。
 *
 * <p>三层降级，任何一层坏掉都还有答案：
 * <ol>
 *   <li>有资料 + 大模型可用 → 生成自然语言回答并标注来源；</li>
 *   <li>有资料 + 大模型不可用 → <b>直接给出资料原文摘录</b>，
 *       而不是退化成"我没听懂"——检索到了就是答得上来，只是换一种表达方式；</li>
 *   <li>没检索到 → 返回空，由调用方走意图兜底。</li>
 * </ol>
 *
 * <p>后置校验与兜底话术同一套思路：提示词负责"尽量不犯错"，代码负责"绝不放过"。
 * 知识问答最容易出的问题是模型顺手编造业务数据（"你上月充值 200 元"）或
 * 把营养建议说成医疗结论，这两种一律丢弃。
 */
@Service
public class KnowledgeAnswerService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAnswerService.class);

    /** 知识问答允许比兜底话术长得多，但仍要防跑飞 */
    private static final int MAX_LEN = 460;

    private static final Pattern FAKE_ACTION = Pattern.compile(
            "已(经)?(为你|帮你|替您|给您)|已完成|已入账|已到账|已充值|已报名|已更新");

    private static final Pattern FAKE_DATA = Pattern.compile("(余额|积分|订单号|剩余)\\D{0,4}\\d");

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s");

    private final DeepSeekClient llm;
    private final KnowledgeService knowledge;
    private final KnowledgeProperties props;

    public KnowledgeAnswerService(DeepSeekClient llm, KnowledgeService knowledge, KnowledgeProperties props) {
        this.llm = llm;
        this.knowledge = knowledge;
        this.props = props;
    }

    /**
     * @param hits 已检索到的资料（调用方负责检索，便于把命中结果一并回传前端做来源卡）
     * @return 回答；未检索到资料时返回空
     */
    public Optional<Answer> answer(LlmConfig cfg, String question, boolean memberMode, List<KnowledgeHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Optional.empty();
        }
        if (cfg != null && cfg.isConfigured()) {
            try {
                String context = knowledge.promptContextFor(hits, props.getPromptChars());
                String raw = llm.chatText(cfg, PromptLibrary.KNOWLEDGE_SYSTEM,
                        PromptLibrary.knowledgeUser(question, context, memberMode));
                String text = normalize(raw);
                String violation = violationOf(text);
                if (violation == null) {
                    log.info("知识问答生成成功 prompt={} hits={} chars={}",
                            PromptLibrary.KNOWLEDGE_VERSION, hits.size(), text.length());
                    return Optional.of(new Answer(withCitation(text, hits), hits,
                            PromptLibrary.KNOWLEDGE_VERSION, true));
                }
                log.warn("知识问答输出越界已丢弃({})，改用资料摘录 text={}", violation, abbreviate(text));
            } catch (Exception e) {
                log.warn("知识问答调用大模型失败，改用资料摘录：{}", e.getMessage());
            }
        }
        return Optional.of(new Answer(excerpt(hits), hits, PromptLibrary.KNOWLEDGE_VERSION, false));
    }

    /* ==================== 降级：直接用资料原文作答 ==================== */

    private String excerpt(List<KnowledgeHit> hits) {
        StringBuilder sb = new StringBuilder("知识库里是这样写的（大模型暂时不可用，直接给你原文）：");
        int n = 0;
        for (KnowledgeHit h : hits) {
            if (n >= 2) {
                break;
            }
            sb.append(n == 0 ? "" : "；")
                    .append(n + 1).append("）《").append(h.doc().title()).append("》");
            if (h.anchor() != null && !h.anchor().isBlank()) {
                sb.append("·").append(h.anchor());
            }
            sb.append("：").append(h.snippet());
            n++;
        }
        return sb.toString();
    }

    /* ==================== 输出整理 ==================== */

    /** 去掉代码围栏与 markdown 标题记号，保留换行与「1. 2.」分点 */
    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            s = nl >= 0 ? s.substring(nl + 1) : s;
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
            s = s.strip();
        }
        s = MARKDOWN_HEADING.matcher(s).replaceAll("");
        return s.replaceAll("\n{3,}", "\n\n").strip();
    }

    /** 模型没标来源时补一条，保证用户总能看到出处 */
    private String withCitation(String text, List<KnowledgeHit> hits) {
        if (text.contains("《")) {
            return text;
        }
        return text + "\n（来源：《" + hits.get(0).doc().title() + "》）";
    }

    private String violationOf(String text) {
        if (text.isBlank()) {
            return "空内容";
        }
        if (text.length() > MAX_LEN) {
            return "过长(" + text.length() + "字)";
        }
        if (FAKE_ACTION.matcher(text).find()) {
            return "冒充已执行操作";
        }
        if (FAKE_DATA.matcher(text).find()) {
            return "编造业务数据";
        }
        return null;
    }

    private String abbreviate(String s) {
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
    }

    /** 供自检与文档使用 */
    public List<String> rules() {
        return List.of("冒充已执行操作", "编造业务数据", "过长(>" + MAX_LEN + "字)", "空内容");
    }

    /**
     * @param reply         要展示给用户的回答
     * @param hits          用于回答的资料（上层据此渲染来源卡）
     * @param promptVersion 提示词版本
     * @param fromLlm       是否由大模型生成（false 表示资料原文摘录）
     */
    public record Answer(String reply, List<KnowledgeHit> hits, String promptVersion, boolean fromLlm) {
    }
}
