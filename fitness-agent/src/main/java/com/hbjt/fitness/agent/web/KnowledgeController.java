package com.hbjt.fitness.agent.web;

import com.hbjt.fitness.agent.knowledge.KnowledgeDocument;
import com.hbjt.fitness.agent.knowledge.KnowledgeHit;
import com.hbjt.fitness.agent.knowledge.KnowledgeService;
import com.hbjt.fitness.agent.support.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库浏览与检索接口。
 *
 * <p>对话侧用的是内部调用（{@link KnowledgeService#search}），这个控制器是给
 * 前端「知识库」页面和人工排查用的：能直接看到检索命中了哪篇、拿了哪一段、
 * 得分多少。RAG 出问题时，第一件想做的事就是"看看它到底检索到了什么"。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return knowledge.stats();
    }

    /** 文档列表（不含正文），可按分类筛选 */
    @GetMapping("/docs")
    public List<Map<String, Object>> docs(@RequestParam(required = false) String category) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeDocument d : knowledge.list(category)) {
            out.add(d.toSummaryMap());
        }
        return out;
    }

    /** 单篇全文 */
    @GetMapping("/docs/{id}")
    public Map<String, Object> doc(@PathVariable String id) {
        KnowledgeDocument d = knowledge.byId(id)
                .orElseThrow(() -> new NotFoundException("知识库中没有这篇文档：" + id));
        Map<String, Object> m = d.toSummaryMap();
        m.put("sections", d.sections());
        m.put("body", d.body());
        m.put("path", d.path());
        return m;
    }

    /** 检索：用于验证检索质量与排查 RAG 问题 */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam("q") String q,
                                      @RequestParam(required = false) Integer topK,
                                      @RequestParam(required = false) String category) {
        int k = topK == null || topK <= 0 ? 5 : Math.min(topK, 20);
        Set<String> categories = (category == null || category.isBlank()) ? Set.of() : Set.of(category);
        List<KnowledgeHit> hits = knowledge.search(q, k, categories);

        List<Map<String, Object>> items = new ArrayList<>();
        for (KnowledgeHit h : hits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.doc().id());
            m.put("title", h.doc().title());
            m.put("category", h.doc().category());
            m.put("categoryLabel", h.doc().categoryLabel());
            m.put("riskLevel", h.doc().riskLevel());
            m.put("score", Math.round(h.score() * 100) / 100.0);
            m.put("anchor", h.anchor());
            m.put("snippet", h.snippet());
            m.put("citation", h.citation());
            items.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("query", q);
        res.put("threshold", knowledge.stats().get("minScore"));
        // 最高分不受阈值影响：命中为空时，它能区分"没覆盖"和"差一点没过线"
        res.put("bestScore", Math.round(knowledge.bestScore(q) * 100) / 100.0);
        res.put("count", items.size());
        res.put("hits", items);
        return res;
    }
}
