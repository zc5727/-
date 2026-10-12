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
            // ---------------------------------------------------------------
            // 可观测性：一次对话 = 一条 trace，内部每个阶段 = 若干 span。
            // 写这张表的动机是"没有度量就没有优化"：此前一次调用耗时多少、
            // 命中了哪篇文档、有没有走降级，全靠翻日志猜。有了 trace 之后，
            // 延迟分布、意图占比、失败率、Token 消耗都能直接算出来。
            //
            // 与 chat_message 的分工：chat_message 存"用户看得到的内容"，
            // 用于会话回放；llm_trace 存"这次调用发生了什么"，用于运维分析。
            // 两者用 session_key 关联，职责不重叠。
            // ---------------------------------------------------------------
            """
            CREATE TABLE IF NOT EXISTS llm_trace (
              id               BIGINT      NOT NULL AUTO_INCREMENT,
              trace_id         VARCHAR(40) NOT NULL COMMENT '本次调用唯一标识',
              session_key      VARCHAR(64) NULL COMMENT '会话标识，用于与会话回放关联',
              owner_type       VARCHAR(16) NULL COMMENT 'member / admin / guest',
              owner_id         VARCHAR(64) NULL COMMENT '归属标识',
              intent           VARCHAR(32) NULL COMMENT '路由出的意图',
              channel          VARCHAR(24) NULL COMMENT 'plan / knowledge / fallback / chat',
              model            VARCHAR(64) NULL COMMENT '实际使用的模型',
              prompt_version   VARCHAR(32) NULL COMMENT '提示词版本，用于对比不同版本效果',
              status           VARCHAR(16) NOT NULL COMMENT 'OK / ASK / DEGRADED / ERROR',
              error_type       VARCHAR(64) NULL COMMENT '异常类型，便于归因',
              degraded         TINYINT     NOT NULL DEFAULT 0 COMMENT '是否走了降级路径',
              latency_ms       INT         NOT NULL DEFAULT 0 COMMENT '端到端耗时（毫秒）',
              prompt_tokens    INT         NULL,
              completion_tokens INT        NULL,
              total_tokens     INT         NULL,
              hit_count        INT         NULL COMMENT '知识检索命中文档数',
              top_score        DOUBLE      NULL COMMENT '知识检索最高得分',
              user_text        VARCHAR(500) NULL COMMENT '用户输入摘要，便于排查',
              created_at       DATETIME    NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_trace_id (trace_id),
              KEY idx_created (created_at),
              KEY idx_intent_created (intent, created_at),
              KEY idx_owner_created (owner_type, owner_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型调用主记录'
            """,
            """
            CREATE TABLE IF NOT EXISTS llm_span (
              id          BIGINT      NOT NULL AUTO_INCREMENT,
              trace_id    VARCHAR(40) NOT NULL,
              span_name   VARCHAR(48) NOT NULL COMMENT '阶段名：llm.chat / knowledge.search / db.append 等',
              kind        VARCHAR(16) NOT NULL COMMENT 'llm / retrieve / db / tool',
              start_ms    INT         NOT NULL DEFAULT 0 COMMENT '相对 trace 起点的偏移（毫秒）',
              duration_ms INT         NOT NULL DEFAULT 0,
              detail      VARCHAR(500) NULL COMMENT '阶段补充信息，如命中文档 id',
              created_at  DATETIME    NOT NULL,
              PRIMARY KEY (id),
              KEY idx_trace (trace_id, start_ms)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调用内部阶段耗时'
            """,
            """
            CREATE TABLE IF NOT EXISTS agent_daily_metric (
              id           BIGINT     NOT NULL AUTO_INCREMENT,
              metric_date  DATE       NOT NULL,
              metric_key   VARCHAR(48) NOT NULL COMMENT 'calls / errors / degraded / tokens / p95_latency 等',
              metric_value DOUBLE     NOT NULL DEFAULT 0,
              updated_at   DATETIME   NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_date_key (metric_date, metric_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按天聚合的智能体运行指标'
            """,
            // ---------------------------------------------------------------
            // 反馈闭环：用户按下"有用/没用"，系统据此调整该文档在检索中的权重。
            // 这样知识库会越用越准，而不只是"一次配好、永远不变"。
            // knowledge_feedback 是流水（每条反馈一行，可追溯），
            // knowledge_doc_weight 是聚合结果（每篇文档一行，直接参与打分）。
            // 分开存是为了检索时只查聚合表，不必每次扫全量流水。
            // ---------------------------------------------------------------
            """
            CREATE TABLE IF NOT EXISTS knowledge_feedback (
              id          BIGINT       NOT NULL AUTO_INCREMENT,
              trace_id    VARCHAR(40)  NULL COMMENT '关联当次调用',
              session_key VARCHAR(64)  NULL,
              owner_type  VARCHAR(16)  NULL,
              owner_id    VARCHAR(64)  NULL,
              query       VARCHAR(300) NOT NULL COMMENT '用户当时的提问',
              doc_id      VARCHAR(120) NULL COMMENT '被评价的文档',
              doc_title   VARCHAR(200) NULL,
              score       DOUBLE       NULL COMMENT '当次检索得分，用于回看阈值是否合理',
              vote        VARCHAR(8)   NOT NULL COMMENT 'UP / DOWN',
              comment     VARCHAR(500) NULL,
              created_at  DATETIME     NOT NULL,
              PRIMARY KEY (id),
              KEY idx_created (created_at),
              KEY idx_doc (doc_id),
              KEY idx_owner (owner_type, owner_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识回答反馈流水'
            """,
            """
            CREATE TABLE IF NOT EXISTS knowledge_doc_weight (
              doc_id     VARCHAR(120) NOT NULL,
              doc_title  VARCHAR(200) NULL,
              up_count   INT    NOT NULL DEFAULT 0,
              down_count INT    NOT NULL DEFAULT 0,
              weight     DOUBLE NOT NULL DEFAULT 1.0 COMMENT '参与检索打分的乘数',
              updated_at DATETIME NOT NULL,
              PRIMARY KEY (doc_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档反馈权重'
            """,
            // ---------------------------------------------------------------
            // 计划执行闭环：计划生成后自动拆成每日任务，会员打卡，
            // 系统据此算依从率；依从率过低时，下次生成计划自动降强度。
            // 这把"一次性建议"变成了"持续跟进"，也是健身场景真正值钱的部分。
            // ---------------------------------------------------------------
            """
            CREATE TABLE IF NOT EXISTS plan_task (
              id           BIGINT      NOT NULL AUTO_INCREMENT,
              plan_key     VARCHAR(64) NOT NULL COMMENT '计划标识，同一计划共享',
              session_key  VARCHAR(64) NULL,
              owner_type   VARCHAR(16) NOT NULL,
              owner_id     VARCHAR(64) NOT NULL,
              day_index    INT         NOT NULL COMMENT '第几天，从 1 起',
              task_type    VARCHAR(16) NOT NULL COMMENT 'cardio / strength / rest',
              title        VARCHAR(200) NOT NULL,
              detail_json  VARCHAR(1000) NULL,
              planned_date DATE        NOT NULL,
              status       VARCHAR(16) NOT NULL DEFAULT 'TODO' COMMENT 'TODO / DONE / SKIPPED',
              created_at   DATETIME    NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_plan_day (plan_key, day_index),
              KEY idx_owner_date (owner_type, owner_id, planned_date)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划拆解出的每日任务'
            """,
            """
            CREATE TABLE IF NOT EXISTS plan_task_log (
              id           BIGINT      NOT NULL AUTO_INCREMENT,
              task_id      BIGINT      NOT NULL,
              owner_type   VARCHAR(16) NOT NULL,
              owner_id     VARCHAR(64) NOT NULL,
              action       VARCHAR(16) NOT NULL COMMENT 'DONE / SKIP / UNDO',
              actual_value VARCHAR(200) NULL COMMENT '实际完成量，如 30 分钟',
              note         VARCHAR(500) NULL,
              created_at   DATETIME    NOT NULL,
              PRIMARY KEY (id),
              KEY idx_task (task_id),
              KEY idx_owner (owner_type, owner_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划任务打卡流水'
            """,
            // ---------------------------------------------------------------
            // 离线评测：把回归用例沉淀成可反复执行的评测集。
            // eval_case 是题目，eval_run 是每次跑批，eval_result 是逐题结果。
            // 有了它，"这次改动有没有让回答变差"才是个能回答的问题。
            // ---------------------------------------------------------------
            """
            CREATE TABLE IF NOT EXISTS eval_case (
              id              BIGINT       NOT NULL AUTO_INCREMENT,
              category        VARCHAR(32)  NOT NULL COMMENT '意图路由 / 知识问答 / 计划生成 等',
              question        VARCHAR(300) NOT NULL,
              expect_intent   VARCHAR(32)  NULL COMMENT '期望意图',
              expect_keywords VARCHAR(300) NULL COMMENT '回答里应出现的关键词，逗号分隔',
              enabled         TINYINT      NOT NULL DEFAULT 1,
              created_at      DATETIME     NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_question (question)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测用例'
            """,
            """
            CREATE TABLE IF NOT EXISTS eval_run (
              id             BIGINT   NOT NULL AUTO_INCREMENT,
              started_at     DATETIME NOT NULL,
              finished_at    DATETIME NULL,
              total_cases    INT      NOT NULL DEFAULT 0,
              passed         INT      NOT NULL DEFAULT 0,
              failed         INT      NOT NULL DEFAULT 0,
              avg_latency_ms INT      NULL,
              summary_json   VARCHAR(2000) NULL,
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测批次'
            """,
            """
            CREATE TABLE IF NOT EXISTS eval_result (
              id            BIGINT      NOT NULL AUTO_INCREMENT,
              run_id        BIGINT      NOT NULL,
              case_id       BIGINT      NOT NULL,
              actual_intent VARCHAR(32) NULL,
              intent_ok     TINYINT     NOT NULL DEFAULT 0,
              keyword_hits  INT         NOT NULL DEFAULT 0 COMMENT '命中的关键词数',
              keyword_total INT         NOT NULL DEFAULT 0 COMMENT '应命中的关键词总数',
              score         DOUBLE      NOT NULL DEFAULT 0 COMMENT '0-100',
              latency_ms    INT         NOT NULL DEFAULT 0,
              ok            TINYINT     NOT NULL DEFAULT 0,
              detail        VARCHAR(500) NULL,
              PRIMARY KEY (id),
              KEY idx_run (run_id),
              KEY idx_case (case_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测逐题结果'
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
