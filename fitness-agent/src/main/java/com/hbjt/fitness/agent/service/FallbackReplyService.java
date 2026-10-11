package com.hbjt.fitness.agent.service;

import com.hbjt.fitness.agent.client.DeepSeekClient;
import com.hbjt.fitness.agent.config.LlmConfig;
import com.hbjt.fitness.agent.prompt.PromptLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 意图兜底话术生成。
 *
 * <p>解决一个具体问题：用户说的话没能归入任何已支持的业务动作时，
 * 固定话术（"这句我没听懂"）在连续几次之后会显得很笨，也答不到点上。
 * 这里让大模型生成一句自然、贴合的引导话术。
 *
 * <p>但兜底模块是资金系统里最危险的地方——模型很可能热情过头，说出
 * 「已为你充值」「你的余额是 500 元」这种"什么都没发生但用户以为发生了"的话。
 * 因此提示词里写了硬约束，代码里再叠一层<b>后置校验</b>：
 * 生成的话术只要出现完成态表述或业务数据，一律丢弃，回退到固定话术。
 * 提示词负责"尽量不犯错"，校验负责"绝不放过"。
 *
 * <p>任何异常（未配置密钥、超时、越界）都返回 {@link Optional#empty()}，
 * 调用方无脑回退固定话术即可——兜底模块自己绝不能成为新的故障点。
 */
@Service
public class FallbackReplyService {

    private static final Logger log = LoggerFactory.getLogger(FallbackReplyService.class);

    /** 合规话术长度上限：提示词要求 80 字以内，留一点余量 */
    private static final int MAX_LEN = 120;

    /** 冒充实操的完成态表述——最危险的越界 */
    private static final Pattern FAKE_ACTION = Pattern.compile(
            "已(经)?(为你|帮你|替您|给您)|已完成|已办|已处理|已入账|已到账|已充值|已报名|已更新|正在为你|为你办理");

    /** 编造业务数据：出现这些字段名 + 数字，说明模型在虚构系统里的真实数据 */
    private static final Pattern FAKE_DATA = Pattern.compile(
            "(余额|积分|订单号|余额为|剩余)\\D{0,4}\\d");

    /** markdown / 分点 / 代码块迹象 */
    private static final Pattern MARKDOWN = Pattern.compile("(```|\\*\\*|^\\s*[-•*]\\s|^\\s*\\d+[.、]\\s)", Pattern.MULTILINE);

    private final DeepSeekClient llm;

    public FallbackReplyService(DeepSeekClient llm) {
        this.llm = llm;
    }

    /**
     * @return 合规的引导话术；未配置 / 失败 / 越界均返回 empty，调用方回退固定话术
     */
    public Optional<String> guide(LlmConfig cfg, String userText, boolean memberMode) {
        if (cfg == null || !cfg.isConfigured() || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = llm.chatText(cfg, PromptLibrary.FALLBACK_SYSTEM,
                    PromptLibrary.fallbackUser(userText, memberMode, PromptLibrary.capabilityLines()));
            String text = normalize(raw);
            String violation = violationOf(text);
            if (violation != null) {
                log.warn("兜底话术越界已丢弃({})，回退固定话术 text={}", violation, abbreviate(text));
                return Optional.empty();
            }
            log.info("兜底话术生成成功 prompt={} chars={}", PromptLibrary.FALLBACK_VERSION, text.length());
            return Optional.of(text);
        } catch (Exception e) {
            log.warn("兜底话术生成失败，回退固定话术：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 去掉模型偶尔带上的围栏、外层引号与多余空行 */
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
        // 整句被引号包起来的情况
        if (s.length() > 1) {
            char c0 = s.charAt(0);
            char c1 = s.charAt(s.length() - 1);
            if ((c0 == '"' && c1 == '"') || (c0 == '“' && c1 == '”')) {
                s = s.substring(1, s.length() - 1).strip();
            }
        }
        return s.replaceAll("\n{2,}", "\n").strip();
    }

    /** @return 违规原因；合规返回 null */
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
        if (MARKDOWN.matcher(text).find()) {
            return "含 markdown 格式";
        }
        if (text.split("\n", -1).length > 2) {
            return "行数过多";
        }
        return null;
    }

    private String abbreviate(String s) {
        return s.length() <= 100 ? s : s.substring(0, 100) + "...";
    }

    /** 供自检与文档使用：当前生效的越界规则条数 */
    public List<String> rules() {
        return List.of("冒充已执行操作", "编造业务数据", "含 markdown 格式", "过长(>120字)", "行数过多(>2行)");
    }
}
