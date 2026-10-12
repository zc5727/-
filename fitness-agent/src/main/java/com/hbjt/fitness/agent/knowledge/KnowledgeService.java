package com.hbjt.fitness.agent.knowledge;

import com.hbjt.fitness.agent.observability.TraceContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 知识库服务：对外提供检索、浏览与"参考资料"拼装。
 *
 * <p>索引在内存里，启动时构建；此后按 {@link KnowledgeProperties#getReloadSeconds()}
 * 周期检查目录签名（文件路径 + 修改时间 + 大小），有变化才重建。
 * 于是知识库可以以卷挂载的方式热更新——改一篇 Markdown，等服务一个刷新周期即可生效，
 * 不必重建镜像、不必重启容器。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private static final String INDEX_FILE = "INDEX.MD";

    private final KnowledgeProperties props;
    private final KnowledgeLoader loader;
    private final KnowledgeFeedbackService feedback;

    private volatile KnowledgeIndex index = new KnowledgeIndex(List.of());
    private volatile String dirSignature = "";
    private volatile long loadedAt = 0;
    private volatile long lastCheck = 0;

    public KnowledgeService(KnowledgeProperties props, KnowledgeLoader loader,
                            KnowledgeFeedbackService feedback) {
        this.props = props;
        this.loader = loader;
        this.feedback = feedback;
    }

    @PostConstruct
    public void init() {
        reload("启动加载");
    }

    /** 目录签名变化时重建索引 */
    @Scheduled(fixedDelay = 20_000L)
    public void refreshIfChanged() {
        if (!props.isEnabled() || props.getReloadSeconds() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheck < props.getReloadSeconds() * 1000L) {
            return;
        }
        lastCheck = now;
        String sig = signature();
        if (!sig.equals(dirSignature)) {
            reload("检测到知识库变更");
        }
    }

    private synchronized void reload(String reason) {
        List<KnowledgeDocument> docs = loader.load();
        this.index = new KnowledgeIndex(docs);
        this.dirSignature = signature();
        this.loadedAt = System.currentTimeMillis();
        log.info("知识库已加载（{}）：{} 篇，正文 {} 字", reason, docs.size(),
                docs.stream().mapToInt(KnowledgeDocument::chars).sum());
    }

    /* ==================== 检索 ==================== */

    /** 默认参数检索 */
    public List<KnowledgeHit> search(String query) {
        return search(query, props.getMaxResults(), Set.of());
    }

    public List<KnowledgeHit> search(String query, int topK, Set<String> categories) {
        TraceContext.Trace trace = TraceContext.current();
        try (TraceContext.Span ignored = TraceContext.span("knowledge.search", "retrieve")) {
            List<KnowledgeHit> hits = index.search(query, topK, categories,
                    props.getMinScore(), props.getSnippetChars());
            List<KnowledgeHit> ranked = rerankByFeedback(hits);
            if (trace != null) {
                // 记录命中情况：结合阈值就能判断"拒答"到底是词表没覆盖还是差一点点
                trace.hitCount = ranked.size();
                trace.topScore = ranked.isEmpty() ? 0.0 : ranked.get(0).score();
            }
            return ranked;
        }
    }

    /**
     * 用反馈权重重排。
     *
     * <p>只改排序，不做二次过滤——相关度阈值已经在 {@code index.search} 里判过了。
     * 这里再按权重砍一刀的话，连点几次「没用」就能让一篇文档彻底消失，
     * 等于把下架权交给了误触，风险远大于收益。
     */
    private List<KnowledgeHit> rerankByFeedback(List<KnowledgeHit> hits) {
        if (hits.size() <= 1) {
            return hits;
        }
        Map<String, Double> weights = feedback.weights();
        if (weights.isEmpty()) {
            return hits;
        }
        List<KnowledgeHit> out = new ArrayList<>(hits.size());
        for (KnowledgeHit h : hits) {
            double m = weights.getOrDefault(h.doc().id(), 1.0);
            out.add(new KnowledgeHit(h.doc(), h.score() * m, h.snippet(), h.anchor()));
        }
        out.sort(Comparator.comparingDouble(KnowledgeHit::score).reversed());
        return out;
    }

    /** 是否"答得上来"——决定未知意图走知识库还是走兜底话术 */
    public boolean canAnswer(String query) {
        return !index.search(query, 1, Set.of(), props.getMinScore(), props.getSnippetChars()).isEmpty();
    }

    /**
     * 全库最高分，不受阈值影响。
     *
     * <p>它是"为什么这句没答上来"的答案：最高分远低于阈值说明词表没覆盖，
     * 只差一点则说明只是没过线，两种情况该采取的措施完全不同。
     */
    public double bestScore(String query) {
        return index.bestScore(query, Set.of());
    }

    /* ==================== 浏览 ==================== */

    public Optional<KnowledgeDocument> byId(String id) {
        for (KnowledgeDocument d : index.documents()) {
            if (d.id().equals(id)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    public List<KnowledgeDocument> list(String category) {
        List<KnowledgeDocument> out = new ArrayList<>();
        for (KnowledgeDocument d : index.documents()) {
            if (category == null || category.isBlank() || category.equals(d.category())) {
                out.add(d);
            }
        }
        out.sort(Comparator.comparing(KnowledgeDocument::category).thenComparing(KnowledgeDocument::id));
        return out;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<KnowledgeDocument> docs = index.documents();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (KnowledgeDocument d : docs) {
            byCategory.merge(d.category(), 1, Integer::sum);
        }
        m.put("enabled", props.isEnabled());
        m.put("dir", props.getDir());
        m.put("total", docs.size());
        m.put("byCategory", byCategory);
        m.put("chars", docs.stream().mapToInt(KnowledgeDocument::chars).sum());
        m.put("sections", docs.stream().mapToInt(d -> d.sections().size()).sum());
        m.put("minScore", props.getMinScore());
        m.put("loadedAt", loadedAt);
        m.put("reloadSeconds", props.getReloadSeconds());
        return m;
    }

    public boolean isReady() {
        return index.size() > 0;
    }

    /* ==================== 提示词注入 ==================== */

    /**
     * 把检索结果拼成可直接塞进提示词的参考资料块。
     *
     * <p>有一句必须写死的前提声明：<b>资料里的数值不是本次会员的身体数据</b>。
     * 计划生成提示词的第一条铁律是"只用给定数值、禁止编造"，
     * 如果不说清资料的定位，模型很容易把资料里的"每周 3 次"当成会员的实测指标复述出来，
     * 反而破坏了证据链。
     */
    public String promptContext(String query, Set<String> categories, int maxChars) {
        List<KnowledgeHit> hits = search(query, props.getMaxResults(), categories);
        return renderContext(hits, maxChars);
    }

    public String promptContextFor(List<KnowledgeHit> hits, int maxChars) {
        return renderContext(hits, maxChars);
    }

    private String renderContext(List<KnowledgeHit> hits, int maxChars) {
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【参考资料（来自本站健身知识库）】\n")
                .append("使用方法：组织语言时可以借鉴以下内容，并在回答中标注来源标题。")
                .append("注意：资料里的数值（如次数、时长、热量）是通用建议，")
                .append("不是本次会员的身体数据，不要当作会员的实测指标复述。\n")
                .append("每条资料给两行：『要点』是这篇的核心结论，『命中』是与提问最贴近的段落；")
                .append("两行都要看，不要因为命中段落没写就断言资料没写。\n");
        int used = 0;
        int n = 0;
        for (KnowledgeHit h : hits) {
            StringBuilder block = new StringBuilder();
            block.append(n + 1).append(". 《").append(h.doc().title()).append("》")
                    .append(h.anchor() == null ? "" : " · " + h.anchor()).append("\n");
            // 只给命中段落是不够的：段落是按词面相似度挑的，容易挑到"常见误区"这种边角内容，
            // 于是模型会得出"资料里没写"的错误结论。把这篇的要点一并给出，结论才完整。
            String keypoint = keypoint(h.doc());
            if (keypoint != null) {
                block.append("   要点：").append(keypoint).append("\n");
            }
            block.append("   命中：").append(h.snippet()).append("\n");
            if (used + block.length() > maxChars) {
                break;
            }
            sb.append(block);
            used += block.length();
            n++;
        }
        return n == 0 ? "" : sb.toString();
    }

    /** 取这篇文档的核心结论章节（不同分类的命名不一样），截断到 180 字 */
    private static String keypoint(KnowledgeDocument d) {
        for (String name : new String[]{"要点", "是什么", "适用人群", "说明"}) {
            String text = d.sections().get(name);
            if (text != null && !text.isBlank()) {
                String flat = text.strip().replaceAll("\\s+", " ");
                return flat.length() <= 180 ? flat : flat.substring(0, 180) + "…";
            }
        }
        return null;
    }

    /** 引用标签列表，供前端"来源"卡片展示 */
    public static List<String> citations(List<KnowledgeHit> hits) {
        List<String> out = new ArrayList<>();
        for (KnowledgeHit h : hits) {
            out.add(h.citation());
        }
        return out;
    }

    /* ==================== 工具 ==================== */

    /** 目录签名：路径|修改时间|大小。用来判断"知识库有没有被改过"。 */
    private String signature() {
        if (!props.isEnabled()) {
            return "disabled";
        }
        Path root = Path.of(props.getDir());
        if (!Files.isDirectory(root)) {
            return "missing";
        }
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                    .filter(p -> !INDEX_FILE.equalsIgnoreCase(p.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        try {
                            sb.append(root.relativize(p)).append('|')
                                    .append(Files.getLastModifiedTime(p).toMillis()).append('|')
                                    .append(Files.size(p)).append(';');
                        } catch (IOException ignored) {
                            // 文件刚好被替换，忽略，下个周期会重新计算
                        }
                    });
        } catch (IOException e) {
            return "error";
        }
        return Integer.toHexString(sb.toString().hashCode());
    }
}
