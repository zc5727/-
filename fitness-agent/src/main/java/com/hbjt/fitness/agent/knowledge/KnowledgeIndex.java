package com.hbjt.fitness.agent.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内存检索索引（BM25 + 字段加权 + 分类先验）。
 *
 * <p>为什么不直接上向量检索：
 * <ol>
 *   <li>语料只有几十篇、每篇几百字，词法检索在这类规模上召回与精度都够用；</li>
 *   <li>向量检索需要 embedding 服务与向量库，等于给智能体增加一个外部依赖——
 *       而这个知识库本身是要能"挂上去就跑"的；</li>
 *   <li>词法检索<b>可解释</b>：为什么命中、命中了什么词，都能复现，
 *       RAG 出问题时排查成本低得多。</li>
 * </ol>
 *
 * <p>打分由三部分组成：
 * <ol>
 *   <li><b>整串命中</b>：用户原话包含某篇的「常见问法」或标题——最强信号，直接加高分；</li>
 *   <li><b>BM25</b>：二元组 token 加权累加，标题／问法／标签字段权重 3，正文权重 1；</li>
 *   <li><b>分类先验</b>：问"计划/一周几次"偏向 plan，问"疼/头晕/高血压"偏向 safety。
 *       只用于打破接近的平分，不改变量级。</li>
 * </ol>
 */
public class KnowledgeIndex {

    /** BM25 饱和参数：越大越倾向于"多出现就线性加分"，1.2 是通用取值 */
    private static final double K1 = 1.2;

    /** 字段权重 */
    private static final double W_BOOST = 3.0;
    private static final double W_BODY = 1.0;

    /** 整串命中加分 */
    private static final double BONUS_QUESTION = 14.0;
    private static final double BONUS_TITLE = 10.0;
    private static final double BONUS_CATEGORY_PRIOR = 2.5;

    /** 分类先验的触发词 */
    private static final Map<String, String[]> CATEGORY_HINTS = Map.of(
            "plan", new String[]{"计划", "安排", "一周", "每周", "几次", "方案", "怎么练", "频率"},
            "safety", new String[]{"疼", "痛", "伤", "头晕", "胸闷", "高血压", "糖尿病", "心脏",
                    "肾", "孕", "手术", "就医", "危险", "晕倒", "气短"},
            "nutrition", new String[]{"吃", "饮食", "蛋白质", "热量", "碳水", "脂肪", "喝水", "外卖"},
            "exercise", new String[]{"怎么做", "动作", "姿势", "要领", "标准", "发力"}
    );

    private final List<KnowledgeDocument> docs;
    private final List<Map<String, Integer>> boostTf = new ArrayList<>();
    private final List<Map<String, Integer>> bodyTf = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();

    public KnowledgeIndex(List<KnowledgeDocument> documents) {
        this.docs = List.copyOf(documents);
        for (KnowledgeDocument d : this.docs) {
            Map<String, Integer> boost = new HashMap<>();
            count(boost, d.title());
            count(boost, d.category());
            count(boost, d.id());
            for (String t : d.tags()) {
                count(boost, t);
            }
            for (String q : d.questions()) {
                count(boost, q);
            }
            Map<String, Integer> body = new HashMap<>();
            count(body, d.body());

            Set<String> seen = new HashSet<>(boost.keySet());
            seen.addAll(body.keySet());
            for (String t : seen) {
                df.merge(t, 1, Integer::sum);
            }
            boostTf.add(boost);
            bodyTf.add(body);
        }
    }

    public int size() {
        return docs.size();
    }

    public List<KnowledgeDocument> documents() {
        return docs;
    }

    /**
     * 检索。
     *
     * @param query      用户原话
     * @param topK       最多返回多少条
     * @param categories 限定分类（空集合表示不限定）
     * @param minScore   命中阈值，低于它不返回
     * @param snippetLen 片段长度
     */
    public List<KnowledgeHit> search(String query, int topK, Set<String> categories,
                                     double minScore, int snippetLen) {
        List<KnowledgeHit> hits = new ArrayList<>();
        if (query == null || query.isBlank() || docs.isEmpty()) {
            return hits;
        }
        String nq = query.strip().toLowerCase();
        List<String> tokens = KnowledgeTokenizer.tokens(nq);
        Set<String> unique = new HashSet<>(tokens);

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            KnowledgeDocument d = docs.get(i);
            if (!categories.isEmpty() && !categories.contains(d.category())) {
                continue;
            }
            double score = score(d, i, nq, tokens, unique);
            if (score >= minScore) {
                scored.add(new Scored(d, score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            KnowledgeDocument d = scored.get(i).doc();
            List<String> uniqueTokens = new ArrayList<>(unique);
            String anchor = bestSection(d, uniqueTokens);
            String snippet = anchor == null
                    ? d.snippetAround(query, snippetLen)
                    : KnowledgeDocument.bestParagraph(d.sections().get(anchor), query, snippetLen);
            hits.add(new KnowledgeHit(d, scored.get(i).score(), snippet, anchor));
        }
        return hits;
    }

    /** 命中得分，供调试与阈值标定使用 */
    public double scoreOf(String query, String id) {
        String nq = query.strip().toLowerCase();
        List<String> tokens = KnowledgeTokenizer.tokens(nq);
        Set<String> unique = new HashSet<>(tokens);
        for (int i = 0; i < docs.size(); i++) {
            if (docs.get(i).id().equals(id)) {
                return score(docs.get(i), i, nq, tokens, unique);
            }
        }
        return 0;
    }

    /**
     * 全库最高分（<b>不受阈值影响</b>）。
     *
     * <p>用途有两个：一是阈值标定——正样本的最低分与负样本的最高分之间那段空白，
     * 才是 minScore 的安全区间；二是线上排查——用户问了一句没得到知识库回答时，
     * "最高分是多少"直接回答了"是词表没覆盖，还是差一点没过线"。
     */
    public double bestScore(String query, Set<String> categories) {
        if (query == null || query.isBlank() || docs.isEmpty()) {
            return 0;
        }
        String nq = query.strip().toLowerCase();
        List<String> tokens = KnowledgeTokenizer.tokens(nq);
        Set<String> unique = new HashSet<>(tokens);
        double best = 0;
        for (int i = 0; i < docs.size(); i++) {
            KnowledgeDocument d = docs.get(i);
            if (categories != null && !categories.isEmpty() && !categories.contains(d.category())) {
                continue;
            }
            best = Math.max(best, score(d, i, nq, tokens, unique));
        }
        return best;
    }

    private double score(KnowledgeDocument d, int idx, String nq, List<String> tokens, Set<String> unique) {
        double score = 0;

        for (String q : d.questions()) {
            if (phraseHit(nq, q)) {
                score += BONUS_QUESTION;
                break;
            }
        }
        String title = d.title() == null ? "" : d.title().toLowerCase();
        if (!title.isEmpty() && (nq.contains(title) || (nq.length() >= 3 && title.contains(nq)))) {
            score += BONUS_TITLE;
        }

        Map<String, Integer> boost = boostTf.get(idx);
        Map<String, Integer> body = bodyTf.get(idx);
        double bm25 = 0;
        int matched = 0;
        for (String t : unique) {
            int bc = boost.getOrDefault(t, 0);
            int dc = body.getOrDefault(t, 0);
            if (bc == 0 && dc == 0) {
                continue;
            }
            matched++;
            bm25 += idf(t) * (W_BOOST * saturate(bc) + W_BODY * saturate(dc));
        }
        double coverage = unique.isEmpty() ? 0 : matched / (double) unique.size();
        // 覆盖率做温和惩罚：只命中一两个 token 的文档不应压过命中大半的文档
        score += bm25 * (0.55 + 0.45 * coverage);

        if (categoryPrior(d.category(), nq)) {
            score += BONUS_CATEGORY_PRIOR;
        }
        return score;
    }

    /**
     * 「整串命中」的判定。
     *
     * <p>两个方向并不对等，必须分开处理：
     * <ul>
     *   <li><b>用户原话包含整条常见问法</b>（"我想问一下深蹲怎么做" ⊃ "深蹲怎么做"）——
     *       这是最强的信号，直接给满分；</li>
     *   <li><b>用户原话是某条常见问法的片段</b>（"什么时候" ⊂ "什么时候加重量"）——
     *       只有片段本身<b>还带实意内容</b>时才算数。
     *       否则纯疑问片段会从一堆问法里白拿高分，这正是不该答的问题被硬答的入口。</li>
     * </ul>
     */
    private static boolean phraseHit(String nq, String question) {
        if (question == null) {
            return false;
        }
        String q = question.strip().toLowerCase();
        if (q.length() < 3) {
            return false;
        }
        if (nq.contains(q)) {
            return true;
        }
        return nq.length() >= 3 && q.contains(nq) && KnowledgeTokenizer.hasContent(nq);
    }

    private static boolean categoryPrior(String category, String nq) {
        String[] hints = CATEGORY_HINTS.get(category);
        if (hints == null) {
            return false;
        }
        for (String h : hints) {
            if (nq.contains(h)) {
                return true;
            }
        }
        return false;
    }

    private double idf(String token) {
        int n = docs.size();
        int d = df.getOrDefault(token, 0);
        return Math.log(1 + (n - d + 0.5) / (d + 0.5));
    }

    private static double saturate(int count) {
        return count <= 0 ? 0 : count * (1 + K1) / (count + K1);
    }

    private static void count(Map<String, Integer> bag, String text) {
        for (String t : KnowledgeTokenizer.tokens(text)) {
            bag.merge(t, 1, Integer::sum);
        }
    }

    /** 命中 token 最集中的章节名 */
    private String bestSection(KnowledgeDocument d, List<String> uniqueTokens) {
        String best = null;
        double bestScore = 0;
        for (Map.Entry<String, String> e : d.sections().entrySet()) {
            List<String> st = KnowledgeTokenizer.tokens(e.getValue());
            if (st.isEmpty()) {
                continue;
            }
            int hit = 0;
            for (String t : uniqueTokens) {
                if (st.contains(t)) {
                    hit++;
                }
            }
            if (hit == 0) {
                continue;
            }
            double s = hit * (1.0 + hit / (double) st.size());
            if (s > bestScore) {
                bestScore = s;
                best = e.getKey();
            }
        }
        return best;
    }

    private record Scored(KnowledgeDocument doc, double score) {
    }
}
