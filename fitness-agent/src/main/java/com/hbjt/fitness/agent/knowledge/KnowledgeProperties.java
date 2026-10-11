package com.hbjt.fitness.agent.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库配置。
 *
 * <p>目录默认指向容器内的 {@code /app/knowledge}，由 docker-compose 以卷挂载方式
 * 映射到仓库的 {@code fitness-agent/knowledge}。也就是说：<b>改知识库不需要重建镜像</b>，
 * 挂载目录里落一篇新 md，服务在下一个刷新周期就会把它检索进来。
 */
@ConfigurationProperties(prefix = "fitness-agent.knowledge")
public class KnowledgeProperties {

    /** 关掉后相关能力整体降级（检索返回空、提示词不注入），但服务照常启动 */
    private boolean enabled = true;

    /** 知识库根目录（容器内路径；本地开发可指向 fitness-agent/knowledge） */
    private String dir = "/app/knowledge";

    /** 单次检索默认返回多少篇 */
    private int maxResults = 3;

    /**
     * 命中阈值：低于它视为「知识库答不上来」，走兜底话术而不是硬答。
     *
     * <p>取值来自实测标定（见 tools/knowledge_test.py）：正样本最低分约 16，
     * 负样本最高分约 9，12 落在两者中间，上下各留约 3 分余量。
     * 这不是拍脑袋的数字——改打分权重或语料规模后应重新跑一遍测试再调。
     */
    private double minScore = 12.0;

    /** 引用片段的最大字数 */
    private int snippetChars = 200;

    /** 注入提示词的参考资料总字数上限（防止把上下文撑爆） */
    private int promptChars = 1400;

    /**
     * 热更新周期（秒）。&lt;=0 表示关闭周期扫描。
     * 默认开启：挂载卷是给人改的，改完还要重启容器就失去了"挂"的意义。
     */
    private int reloadSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getSnippetChars() {
        return snippetChars;
    }

    public void setSnippetChars(int snippetChars) {
        this.snippetChars = snippetChars;
    }

    public int getPromptChars() {
        return promptChars;
    }

    public void setPromptChars(int promptChars) {
        this.promptChars = promptChars;
    }

    public int getReloadSeconds() {
        return reloadSeconds;
    }

    public void setReloadSeconds(int reloadSeconds) {
        this.reloadSeconds = reloadSeconds;
    }
}
