package com.hbjt.fitness.agent.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一篇知识库文档（Markdown 解析后的内存形态）。
 *
 * <p>字段与 front matter 一一对应；{@code sections} 保留章节结构，
 * 这样「回答引用」可以精确到章节（例如引用《徒手深蹲》的「停止条件」），
 * 而不是把整篇正文丢出去。
 */
public record KnowledgeDocument(
        String id,
        String title,
        String category,
        String level,
        List<String> equipment,
        List<String> primaryMuscles,
        String riskLevel,
        List<String> tags,
        List<String> questions,
        Map<String, String> sections,
        String body,
        String path,
        int chars
) {

    /** 分类中文名，用于对用户展示的引用来源 */
    public String categoryLabel() {
        return switch (category == null ? "" : category) {
            case "exercise" -> "动作库";
            case "training" -> "训练基础";
            case "plan" -> "训练计划";
            case "nutrition" -> "营养";
            case "safety" -> "安全";
            default -> category == null ? "知识库" : category;
        };
    }

    /** 难度中文名 */
    public String levelLabel() {
        return switch (level == null ? "" : level) {
            case "beginner" -> "入门";
            case "intermediate" -> "进阶";
            case "advanced" -> "高阶";
            default -> "";
        };
    }

    /**
     * 概述章节的首句——按分类取最能代表这篇的章节。
     * <p>给列表页与引用卡做摘要，避免把正文铺开。
     */
    public String summary() {
        for (String name : summarySections()) {
            String text = sections.get(name);
            if (text != null && !text.isBlank()) {
                return firstSentence(flatten(text));
            }
        }
        List<String> paras = paragraphs();
        return paras.isEmpty() ? "" : firstSentence(paras.get(0));
    }

    private List<String> summarySections() {
        return switch (category == null ? "" : category) {
            case "exercise" -> List.of("动作目标", "训练量建议", "执行步骤");
            case "plan" -> List.of("适用人群", "说明", "训练安排");
            default -> List.of("要点", "是什么", "怎么做");
        };
    }

    /**
     * 取与查询最相关的一段正文作为引用片段。
     *
     * <p>做法：把正文按空行切成段，用查询 token 给每段打分（命中数 + 命中密度），
     * 取最高分段落并截断。这样引用出来的是用户真正问的那一段，
     * 而不是永远固定在开头的概述。
     */
    public String snippetAround(String query, int max) {
        List<String> paras = paragraphs();
        if (paras.isEmpty()) {
            return "";
        }
        return bestParagraph(paras, query, max);
    }

    /**
     * 从一段 Markdown 文本（可以是整篇正文，也可以是某个章节）里挑出与查询最相关的段落。
     *
     * <p>做法：按空行切段，用查询 token 给每段打分（命中数 + 命中密度加权），
     * 取最高分段落并截断。引用出来的是用户真正问的那一段，而不是永远固定在开头的概述。
     */
    public static String bestParagraph(String text, String query, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> paras = new ArrayList<>();
        for (String block : text.split("\n\\s*\n")) {
            String flat = flatten(block);
            if (!flat.isBlank()) {
                paras.add(flat);
            }
        }
        return bestParagraph(paras, query, max);
    }

    private static String bestParagraph(List<String> paras, String query, int max) {
        List<String> qt = KnowledgeTokenizer.tokens(query);
        String best = paras.get(0);
        double bestScore = -1;
        for (String p : paras) {
            List<String> pt = KnowledgeTokenizer.tokens(p);
            if (pt.isEmpty()) {
                continue;
            }
            int hit = 0;
            for (String t : qt) {
                if (pt.contains(t)) {
                    hit++;
                }
            }
            double score = hit * (1.0 + hit / (double) pt.size());
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return truncate(best, max);
    }

    /** 正文段落（已去掉标题行与 Markdown 记号），供检索打分与片段抽取使用 */
    public List<String> paragraphs() {
        List<String> out = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        for (String block : body.split("\n\\s*\n")) {
            String text = flatten(block);
            if (!text.isBlank()) {
                out.add(text);
            }
        }
        return out;
    }

    private static String flatten(String block) {
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\n")) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            l = l.replaceAll("^[-*]\\s+", "").replaceAll("^\\d+\\.\\s+", "");
            l = l.replaceAll("`([a-z0-9_]+)`", "$1");
            if (!l.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(l);
            }
        }
        return sb.toString();
    }

    private static String firstSentence(String text) {
        int idx = -1;
        for (String mark : new String[]{"。", "；", "！", "？"}) {
            int i = text.indexOf(mark);
            if (i >= 0 && (idx < 0 || i < idx)) {
                idx = i;
            }
        }
        String s = idx >= 0 ? text.substring(0, idx + 1) : text;
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** 供接口输出：不含正文全量，只给元数据 + 摘要 */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("category", category);
        m.put("categoryLabel", categoryLabel());
        m.put("level", level);
        m.put("levelLabel", levelLabel());
        m.put("riskLevel", riskLevel);
        m.put("equipment", equipment);
        m.put("primaryMuscles", primaryMuscles);
        m.put("tags", tags);
        m.put("questions", questions);
        m.put("sections", new ArrayList<>(sections.keySet()));
        m.put("summary", summary());
        m.put("chars", chars);
        return m;
    }
}
