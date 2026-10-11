package com.hbjt.fitness.agent.db;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话存储配置。
 *
 * <p>指向智能体<b>自己的</b> schema（默认 {@code fitness_ai}），与业务库
 * {@code gym_management} 完全分离：业务数据依旧只经 REST 读取，
 * 智能体不会、也不应该往业务表里写任何东西。
 *
 * <p>数据库连不上时整个存储层静默降级为"不落库"，对话功能不受影响——
 * 会话历史是增值能力，不该成为可用性的单点。
 */
@ConfigurationProperties(prefix = "fitness-agent.db")
public class DbProperties {

    private boolean enabled = true;

    /** 带 schema 的 JDBC 地址；启动时会先确保该 schema 存在 */
    private String url = "jdbc:mysql://mysql:3306/fitness_ai?useUnicode=true&characterEncoding=UTF-8"
            + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    private String username = "root";

    private String password = "";

    /** 单会话最多返回的历史消息条数 */
    private int historyLimit = 200;

    /** 单条消息最大保存字数，超长截断 */
    private int maxMessageChars = 4000;

    /** 会话保留天数，0 表示不清理 */
    private int retentionDays = 0;

    /** 建连超时（毫秒），调小是为了"数据库挂了也别拖慢启动" */
    private long connectTimeoutMs = 3000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }

    public int getMaxMessageChars() {
        return maxMessageChars;
    }

    public void setMaxMessageChars(int maxMessageChars) {
        this.maxMessageChars = maxMessageChars;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }
}
