package com.hbjt.fitness.agent.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文友好的轻量分词器。
 *
 * <p>为什么不引入分词库（结巴 / HanLP 等）：
 * <ol>
 *   <li>知识库只有几十篇、查询都是十几字的短句，分词精度不是瓶颈，召回才是；</li>
 *   <li>二元切分（bigram）对中文检索的效果在短文本上已接近分词方案，
 *       而且<b>永远不会切错词</b>——"深蹲怎么做"切成 深蹲/蹲怎/怎么/么做，
 *       其中"深蹲""怎么"必然命中语料里的同名字串；</li>
 *   <li>不引入额外依赖，镜像不变大，也不需要在启动时加载几百 MB 的词典。</li>
 * </ol>
 *
 * <p>做法：把文本切成「ASCII 词」与「中文字二元组」两类 token。
 * 拼音、全角字母、下划线 id 都按 ASCII 词处理；连续汉字按相邻两字滑动切片。
 *
 * <p><b>只由功能字组成的二元组一律剔除</b>（见 {@link #FILLER}）。二元切分的固有噪声就在这儿：
 * "什么时候"会切出 什么 / 么时 / 时候，其中"么时"根本不是词，却因为在语料里罕见而拿到很高的
 * IDF，于是一句"这个月的会员卡什么时候到期"就能在健身知识库里翻出一篇文档来。
 * 功能字组合不承载任何主题信息，无论出现在文档里还是查询里，都只制造噪声，剔除是净收益。
 * 判断方式很直接：<b>两个字都是功能字</b>才剔除——"时间""多久""态度"这类"功能字+实意字"照常保留。
 */
public final class KnowledgeTokenizer {

    private KnowledgeTokenizer() {
    }

    /**
     * 功能字集合：代词、助词、语气词、常见虚词与疑问词素。
     *
     * <p>刻意保持克制——只收"单独出现时不指向任何主题"的字。
     * 数字（一二三次）、量词以外的实词一律不收，因此"一次""两次"仍然是可以命中的内容。
     */
    private static final String FILLER =
            "的了着呢吧啊呀哦嗯嘛哇吗么什怎怎样时候多少几个这那哪谁何"
                    + "是我你他她它们要该让给把被从对为以及且而但如果若则之其此些"
                    + "在于与或都可还也很没不能就会再又曾将上下过";

    /** 是否为汉字（含扩展区外的常用区） */
    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // 基本区
                || (c >= 0x3400 && c <= 0x4DBF);  // 扩展 A
    }

    private static boolean isAsciiWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    /** 功能字：自身不指向任何主题，只起语法作用 */
    private static boolean isFiller(char c) {
        return FILLER.indexOf(c) >= 0;
    }

    /**
     * 切分文本。同一 token 会重复出现，调用方按需自行计数。
     */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        String s = text.toLowerCase();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (isCjk(c)) {
                // 连续汉字段：滑动切二元组；单字段落（只有一个字）保留原字
                int j = i;
                while (j < n && isCjk(s.charAt(j))) {
                    j++;
                }
                int len = j - i;
                if (len == 1) {
                    if (!isFiller(c)) {
                        out.add(s.substring(i, j));
                    }
                } else {
                    for (int k = i; k < j - 1; k++) {
                        if (isFiller(s.charAt(k)) && isFiller(s.charAt(k + 1))) {
                            continue; // 纯功能字组合：不承载主题，剔除
                        }
                        out.add(s.substring(k, k + 2));
                    }
                }
                i = j;
            } else if (isAsciiWordChar(c)) {
                int j = i;
                while (j < n && isAsciiWordChar(s.charAt(j))) {
                    j++;
                }
                String w = s.substring(i, j);
                if (w.length() > 1) {
                    out.add(w);
                }
                i = j;
            } else {
                i++;
            }
        }
        return out;
    }

    /**
     * 这段文本是否还有承载主题的 token。
     *
     * <p>用途：只有当查询本身含有实意内容时，才允许它靠"是某条常见问法的片段"拿加分。
     * 否则"什么时候"这种纯疑问片段会从「什么时候加重量」里白拿一次强命中。
     */
    public static boolean hasContent(String text) {
        return !tokens(text).isEmpty();
    }
}
