package com.hbjt.fitness.agent.knowledge;

/**
 * 一条检索结果。
 *
 * @param doc     命中的文档
 * @param score   相关度得分（越大越相关，阈值见 {@link KnowledgeProperties#getMinScore()}）
 * @param snippet 与查询最相关的正文片段，直接用于展示与提示词注入
 * @param anchor  命中的章节名（用于「引用来源」精确到章节），可能为空
 */
public record KnowledgeHit(KnowledgeDocument doc, double score, String snippet, String anchor) {

    /** 引用标签，形如「动作库 · 徒手深蹲 · 常见错误」 */
    public String citation() {
        StringBuilder sb = new StringBuilder();
        sb.append(doc.categoryLabel()).append(" · ").append(doc.title());
        if (anchor != null && !anchor.isBlank()) {
            sb.append(" · ").append(anchor);
        }
        return sb.toString();
    }
}
