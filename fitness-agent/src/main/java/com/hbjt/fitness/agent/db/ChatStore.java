package com.hbjt.fitness.agent.db;

import com.hbjt.fitness.agent.support.AgentException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话存储（智能体自有库）。
 *
 * <p><b>归属隔离是这个类唯一的硬约束，所以它体现在每一句 SQL 的 WHERE 里</b>：
 * 所有查询、更新、删除都带 {@code owner_type = ? AND owner_id = ?}。
 * 这不是"在业务层记得检查一下"的约定，而是写死在数据访问层——
 * 即使上层传错了 sessionKey，也读不到、写不进别人的会话。
 *
 * <p>失败一律降级：数据库不可用时所有方法返回空值/false，不抛异常中断对话。
 * 之前连不上会有一个冷却期，避免每次请求都去试连（那会把接口拖慢）。
 */
@Component
public class ChatStore {

    private static final Logger log = LoggerFactory.getLogger(ChatStore.class);

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 连不上时的重试冷却，避免每个请求都去试连 */
    private static final long RETRY_COOLDOWN_MS = 30_000L;

    private static final int TITLE_CHARS = 24;
    private static final int PREVIEW_CHARS = 40;

    private final DbProperties props;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private volatile boolean available;
    private volatile String unavailableReason = "尚未初始化";
    private volatile long nextRetryAt;

    public ChatStore(DbProperties props, JdbcTemplate chatJdbcTemplate, TransactionTemplate chatTransactionTemplate) {
        this.props = props;
        this.jdbc = chatJdbcTemplate;
        this.tx = chatTransactionTemplate;
        if (!props.isEnabled()) {
            this.unavailableReason = "会话存储已在配置中关闭";
        }
    }

    @PostConstruct
    public void init() {
        if (props.isEnabled()) {
            tryConnect("启动自举");
        }
    }

    private synchronized boolean tryConnect(String reason) {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            SchemaInitializer.bootstrap(props);
            available = true;
            unavailableReason = null;
            log.info("会话存储可用（{}）", reason);
            return true;
        } catch (Exception e) {
            available = false;
            unavailableReason = e.getMessage();
            nextRetryAt = System.currentTimeMillis() + RETRY_COOLDOWN_MS;
            log.warn("会话存储不可用（{}），会话历史功能降级为不落库：{}", reason, e.getMessage());
            return false;
        }
    }

    private boolean ready() {
        if (available) {
            return true;
        }
        if (!props.isEnabled() || System.currentTimeMillis() < nextRetryAt) {
            return false;
        }
        return tryConnect("重试连接");
    }

    private void degrade(DataAccessException e) {
        available = false;
        unavailableReason = e.getMostSpecificCause() == null ? e.getMessage()
                : e.getMostSpecificCause().getMessage();
        nextRetryAt = System.currentTimeMillis() + RETRY_COOLDOWN_MS;
        log.warn("会话存储访问失败，已降级并对齐冷却期：{}", unavailableReason);
    }

    public boolean isAvailable() {
        return available;
    }

    public String unavailableReason() {
        return unavailableReason;
    }

    /* ==================== 会话 ==================== */

    /**
     * 新建会话。会话标识由服务端生成——绝不采用调用方传来的 key 建会话，
     * 否则等于允许用别人的 key 覆盖别人的会话。
     */
    public Optional<String> createSession(String ownerType, String ownerId, Long memberNo, String firstUserText) {
        if (!ready()) {
            return Optional.empty();
        }
        String key = UUID.randomUUID().toString().replace("-", "");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        try {
            jdbc.update("""
                            INSERT INTO chat_session
                              (session_key, owner_type, owner_id, title, member_no,
                               message_count, last_message_at, created_at, updated_at)
                            VALUES (?,?,?,?,?,0,?,?,?)
                            """,
                    key, ownerType, ownerId, title(firstUserText), memberNo, now, now, now);
            return Optional.of(key);
        } catch (DataAccessException e) {
            degrade(e);
            return Optional.empty();
        }
    }

    /** 校验会话是否属于该归属——所有读写前的第一道闸门 */
    public boolean owned(String sessionKey, String ownerType, String ownerId) {
        if (!ready() || sessionKey == null || sessionKey.isBlank()) {
            return false;
        }
        try {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM chat_session WHERE session_key=? AND owner_type=? AND owner_id=?",
                    Long.class, sessionKey, ownerType, ownerId);
            return n != null && n > 0;
        } catch (DataAccessException e) {
            degrade(e);
            return false;
        }
    }

    public List<ChatSessionSummary> listSessions(String ownerType, String ownerId, int limit) {
        if (!ready()) {
            return List.of();
        }
        try {
            return jdbc.query("""
                            SELECT s.session_key, s.title, s.owner_type, s.member_no, s.message_count,
                                   s.created_at, s.updated_at,
                                   (SELECT m.content FROM chat_message m
                                     WHERE m.session_key = s.session_key
                                     ORDER BY m.seq DESC LIMIT 1) AS preview
                              FROM chat_session s
                             WHERE s.owner_type = ? AND s.owner_id = ?
                             ORDER BY s.updated_at DESC
                             LIMIT ?
                            """,
                    (rs, i) -> new ChatSessionSummary(
                            rs.getString("session_key"),
                            rs.getString("title"),
                            rs.getString("owner_type"),
                            rs.getObject("member_no") == null ? null : rs.getLong("member_no"),
                            rs.getInt("message_count"),
                            abbreviate(rs.getString("preview"), PREVIEW_CHARS),
                            fmt(rs.getTimestamp("created_at")),
                            fmt(rs.getTimestamp("updated_at"))),
                    ownerType, ownerId, limit);
        } catch (DataAccessException e) {
            degrade(e);
            return List.of();
        }
    }

    public Optional<ChatSessionSummary> findSession(String sessionKey, String ownerType, String ownerId) {
        if (!ready() || sessionKey == null || sessionKey.isBlank()) {
            return Optional.empty();
        }
        try {
            List<ChatSessionSummary> list = jdbc.query("""
                            SELECT s.session_key, s.title, s.owner_type, s.member_no, s.message_count,
                                   s.created_at, s.updated_at,
                                   (SELECT m.content FROM chat_message m
                                     WHERE m.session_key = s.session_key
                                     ORDER BY m.seq DESC LIMIT 1) AS preview
                              FROM chat_session s
                             WHERE s.session_key = ? AND s.owner_type = ? AND s.owner_id = ?
                            """,
                    (rs, i) -> new ChatSessionSummary(
                            rs.getString("session_key"),
                            rs.getString("title"),
                            rs.getString("owner_type"),
                            rs.getObject("member_no") == null ? null : rs.getLong("member_no"),
                            rs.getInt("message_count"),
                            abbreviate(rs.getString("preview"), PREVIEW_CHARS),
                            fmt(rs.getTimestamp("created_at")),
                            fmt(rs.getTimestamp("updated_at"))),
                    sessionKey, ownerType, ownerId);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (DataAccessException e) {
            degrade(e);
            return Optional.empty();
        }
    }

    public boolean rename(String sessionKey, String ownerType, String ownerId, String newTitle) {
        if (!ready() || newTitle == null || newTitle.isBlank()) {
            return false;
        }
        try {
            int n = jdbc.update("""
                            UPDATE chat_session SET title = ?, updated_at = ?
                             WHERE session_key = ? AND owner_type = ? AND owner_id = ?
                            """,
                    abbreviate(newTitle.strip(), 60), new Timestamp(System.currentTimeMillis()),
                    sessionKey, ownerType, ownerId);
            return n > 0;
        } catch (DataAccessException e) {
            degrade(e);
            return false;
        }
    }

    /**
     * 删除会话及其全部消息（同一事务，避免留下孤儿消息）。
     *
     * <p>事务在方法体内用 {@link TransactionTemplate} 显式开启，而不是 {@code @Transactional}：
     * 注解式事务会在进入方法前就取连接，数据库不可用时异常直接从代理抛出、绕过下面的 catch。
     */
    public boolean delete(String sessionKey, String ownerType, String ownerId) {
        if (!ready() || !owned(sessionKey, ownerType, ownerId)) {
            return false;
        }
        try {
            Boolean removed = tx.execute(status -> {
                jdbc.update("DELETE FROM chat_message WHERE session_key = ?", sessionKey);
                int n = jdbc.update(
                        "DELETE FROM chat_session WHERE session_key = ? AND owner_type = ? AND owner_id = ?",
                        sessionKey, ownerType, ownerId);
                return n > 0;
            });
            return Boolean.TRUE.equals(removed);
        } catch (DataAccessException e) {
            degrade(e);
            throw new AgentException("删除会话失败，请稍后再试", e.getMessage(), e);
        }
    }

    /* ==================== 消息 ==================== */

    /**
     * 追加一轮消息。
     *
     * <p>序号从 {@code chat_session.message_count} 起算，并用 {@code SELECT ... FOR UPDATE}
     * 锁住会话行——这样同一个会话并发写入也不会串号。
     * 归属不匹配时（count 查不到）静默返回，既不报错也不写入。
     *
     * <p>同 {@link #delete}：事务在方法体内显式开启，保证"数据库不可用"能被下方的 catch 兜住，
     * 而不是让对话接口直接 500。
     */
    public int appendTurn(String sessionKey, String ownerType, String ownerId, List<ChatTurn> turns) {
        if (!ready() || turns == null || turns.isEmpty()) {
            return 0;
        }
        try (com.hbjt.fitness.agent.observability.TraceContext.Span ignored =
                     com.hbjt.fitness.agent.observability.TraceContext.span("db.append", "db")) {
            Integer written = tx.execute(status -> {
                Integer count = jdbc.queryForObject("""
                                SELECT message_count FROM chat_session
                                 WHERE session_key = ? AND owner_type = ? AND owner_id = ?
                                 FOR UPDATE
                                """,
                        Integer.class, sessionKey, ownerType, ownerId);
                if (count == null) {
                    log.warn("会话不属于该归属，拒绝写入 sessionKey={} owner={}:{}", sessionKey, ownerType, ownerId);
                    return 0;
                }
                Timestamp now = new Timestamp(System.currentTimeMillis());
                int seq = count;
                int n = 0;
                for (ChatTurn t : turns) {
                    seq++;
                    n += jdbc.update("""
                                    INSERT IGNORE INTO chat_message
                                      (session_key, seq, role, content, api_content, payload_json,
                                       pending_json, prompt_version, status, created_at)
                                    VALUES (?,?,?,?,?,?,?,?,?,?)
                                    """,
                            sessionKey, seq, t.role(), clip(t.content()), clip(t.apiContent()),
                            clip(t.payloadJson()), clipPending(t.pendingJson()), t.promptVersion(), t.status(), now);
                }
                jdbc.update("""
                                UPDATE chat_session
                                   SET message_count = ?, last_message_at = ?, updated_at = ?
                                 WHERE session_key = ? AND owner_type = ? AND owner_id = ?
                                """,
                        seq, now, now, sessionKey, ownerType, ownerId);
                return n;
            });
            return written == null ? 0 : written;
        } catch (DataAccessException e) {
            degrade(e);
            return 0;
        }
    }

    public List<StoredMessage> listMessages(String sessionKey, String ownerType, String ownerId, int limit) {
        if (!ready() || !owned(sessionKey, ownerType, ownerId)) {
            return List.of();
        }
        try {
            return jdbc.query("""
                            SELECT * FROM (
                              SELECT id, seq, role, content, api_content, payload_json,
                                     pending_json, prompt_version, status, created_at
                                FROM chat_message
                               WHERE session_key = ?
                               ORDER BY seq DESC
                               LIMIT ?
                            ) t ORDER BY t.seq ASC
                            """,
                    (rs, i) -> new StoredMessage(
                            rs.getLong("id"),
                            rs.getInt("seq"),
                            rs.getString("role"),
                            rs.getString("content"),
                            rs.getString("api_content"),
                            rs.getString("payload_json"),
                            rs.getString("pending_json"),
                            rs.getString("prompt_version"),
                            rs.getString("status"),
                            fmt(rs.getTimestamp("created_at"))),
                    sessionKey, limit);
        } catch (DataAccessException e) {
            degrade(e);
            return List.of();
        }
    }

    /* ==================== 运维 ==================== */

    /** 按保留天数清理历史（retentionDays<=0 表示不清理） */
    public int purgeExpired() {
        if (!ready() || props.getRetentionDays() <= 0) {
            return 0;
        }
        try {
            Timestamp before = Timestamp.valueOf(LocalDateTime.now().minusDays(props.getRetentionDays()));
            List<String> keys = jdbc.queryForList(
                    "SELECT session_key FROM chat_session WHERE updated_at < ?", String.class, before);
            for (String key : keys) {
                jdbc.update("DELETE FROM chat_message WHERE session_key = ?", key);
            }
            int n = jdbc.update("DELETE FROM chat_session WHERE updated_at < ?", before);
            if (n > 0) {
                log.info("已清理 {} 个过期会话（保留 {} 天）", n, props.getRetentionDays());
            }
            return n;
        } catch (DataAccessException e) {
            degrade(e);
            return 0;
        }
    }

    /** 存储层概览，供运维接口展示 */
    public Map<String, Object> stats() {
        int sessions = 0;
        int messages = 0;
        if (ready()) {
            try {
                Long s = jdbc.queryForObject("SELECT COUNT(*) FROM chat_session", Long.class);
                Long m = jdbc.queryForObject("SELECT COUNT(*) FROM chat_message", Long.class);
                sessions = s == null ? 0 : s.intValue();
                messages = m == null ? 0 : m.intValue();
            } catch (DataAccessException e) {
                degrade(e);
            }
        }
        return Map.of(
                "available", available,
                "reason", unavailableReason == null ? "" : unavailableReason,
                "sessions", sessions,
                "messages", messages,
                "retentionDays", props.getRetentionDays(),
                "historyLimit", props.getHistoryLimit());
    }

    /* ==================== 工具 ==================== */

    private String clip(String text) {
        if (text == null) {
            return null;
        }
        int max = props.getMaxMessageChars();
        return text.length() <= max ? text : text.substring(0, max) + "…（已截断）";
    }

    /** pending_json 是 VARCHAR(1000)，单独设上限，避免超长导致写库失败 */
    private static String clipPending(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 900 ? text : text.substring(0, 900);
    }

    private static String title(String firstUserText) {
        if (firstUserText == null || firstUserText.isBlank()) {
            return "新会话";
        }
        return abbreviate(firstUserText.strip().replaceAll("\\s+", " "), TITLE_CHARS);
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    private static String fmt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().format(FMT);
    }
}
