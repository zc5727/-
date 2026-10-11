package com.hbjt.fitness.agent.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 会话库自举：建 schema、建表。
 *
 * <p>为什么不用 Flyway/Liquibase：智能体只维护两张表，且是"自带数据库"的形态。
 * 引入迁移框架会带来一堆版本文件与协调成本，而这里需要的能力只是
 * "启动时确认库表存在"。所有语句都是 {@code IF NOT EXISTS}，重复执行无副作用。
 *
 * <p>两阶段建库：先连到不带 schema 的地址把库建出来，再连到目标 schema 建表。
 * 这样即便全新环境也不会出现"库还不存在就先连库"的鸡生蛋问题。
 */
public final class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private SchemaInitializer() {
    }

    private static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS chat_session (
              id              BIGINT       NOT NULL AUTO_INCREMENT,
              session_key     VARCHAR(64)  NOT NULL COMMENT '会话标识，前端持有',
              owner_type      VARCHAR(16)  NOT NULL COMMENT 'member / admin / guest',
              owner_id        VARCHAR(64)  NOT NULL COMMENT '归属标识：会员编号 / 管理员账号 / 浏览器标识',
              title           VARCHAR(120) NOT NULL DEFAULT '' COMMENT '标题，取首句用户发言',
              member_no       INT          NULL COMMENT '会员场景下的会员编号（冗余，便于审计）',
              message_count   INT          NOT NULL DEFAULT 0,
              last_message_at DATETIME     NULL,
              created_at      DATETIME     NOT NULL,
              updated_at      DATETIME     NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_session_key (session_key),
              KEY idx_owner_updated (owner_type, owner_id, updated_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体会话'
            """,
            """
            CREATE TABLE IF NOT EXISTS chat_message (
              id             BIGINT      NOT NULL AUTO_INCREMENT,
              session_key    VARCHAR(64) NOT NULL,
              seq            INT         NOT NULL COMMENT '会话内递增序号',
              role           VARCHAR(16) NOT NULL COMMENT 'user / assistant',
              content        TEXT        NULL COMMENT '展示文本',
              api_content    TEXT        NULL COMMENT '参与语义的文本（按钮种子等）',
              payload_json   MEDIUMTEXT  NULL COMMENT '助手完整响应快照，用于恢复卡片与计划',
              pending_json   VARCHAR(1000) NULL COMMENT '待办快照，恢复后短答才有意义',
              prompt_version VARCHAR(32) NULL COMMENT '本条回复使用的提示词版本',
              status         VARCHAR(16) NULL COMMENT 'OK / ASK / DEGRADED',
              created_at     DATETIME    NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_session_seq (session_key, seq),
              KEY idx_session_created (session_key, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体会话消息'
            """,
    };

    /**
     * 建库建表。抛出的异常由调用方决定如何处理（{@link ChatStore} 会降级处理）。
     */
    public static void bootstrap(DbProperties props) throws SQLException {
        String schema = schemaName(props.getUrl());

        try (Connection conn = DriverManager.getConnection(
                serverUrl(props.getUrl()), props.getUsername(), props.getPassword());
             Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + schema
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            log.info("会话库 schema 就绪：{}", schema);
        } catch (SQLException e) {
            // 没有建库权限时不算致命：库可能已经由运维建好
            log.warn("建库跳过（可能已存在或无权限）：{}", e.getMessage());
        }

        try (Connection conn = DriverManager.getConnection(
                props.getUrl(), props.getUsername(), props.getPassword());
             Statement st = conn.createStatement()) {
            for (String ddl : DDL) {
                st.executeUpdate(ddl);
            }
            log.info("会话表就绪：chat_session / chat_message");
        }
    }

    /** 从 JDBC 地址里取 schema 名 */
    static String schemaName(String url) {
        String rest = stripPrefix(url);
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return "fitness_ai";
        }
        String tail = rest.substring(slash + 1);
        int q = tail.indexOf('?');
        if (q >= 0) {
            tail = tail.substring(0, q);
        }
        return tail.isBlank() ? "fitness_ai" : tail;
    }

    /** 从 JDBC 地址里去掉落库名，得到"服务器级"地址 */
    static String serverUrl(String url) {
        String rest = stripPrefix(url);
        int slash = rest.indexOf('/');
        String base = slash < 0 ? rest : rest.substring(0, slash);
        int q = base.indexOf('?');
        if (q >= 0) {
            base = base.substring(0, q);
        }
        return "jdbc:mysql://" + base + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    private static String stripPrefix(String url) {
        String u = url == null ? "" : url.trim();
        String prefix = "jdbc:mysql://";
        return u.startsWith(prefix) ? u.substring(prefix.length()) : u;
    }
}
