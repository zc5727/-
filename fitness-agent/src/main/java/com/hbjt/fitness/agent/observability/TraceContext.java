package com.hbjt.fitness.agent.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一次智能体调用的链路上下文。
 *
 * <p>用 ThreadLocal 承载，是因为一次对话的完整处理（意图路由 → 知识检索 →
 * 大模型调用 → 落库）都在同一个请求线程里完成，不需要跨线程传递。
 * 这样埋点代码可以写成"从当前线程取"，各层之间不必层层透传 traceId 参数，
 * 对既有代码的侵入最小。
 *
 * <p>关键约束：<b>必须在 finally 里 {@link #clear()}</b>。Tomcat 线程是复用的，
 * 不清理会让下一次请求读到上一次的 trace，指标全部串味。
 *
 * <p>另一点：本模块属于"观测"，任何异常都不允许影响主流程。因此
 * {@link #current()} 在没有开 trace 时返回 {@code null}，各埋点自行判空跳过；
 * 落库失败也只在 {@code TraceRecorder} 内部消化。
 */
public final class TraceContext {

    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    /** 开启一次链路追踪；同线程已有 trace 时直接复用（避免嵌套覆盖）。 */
    public static Trace start(String channel) {
        Trace existing = CURRENT.get();
        if (existing != null) {
            return existing;
        }
        Trace trace = new Trace(newTraceId(), channel);
        CURRENT.set(trace);
        return trace;
    }

    /** 当前线程的 trace；未开启时返回 null。 */
    public static Trace current() {
        return CURRENT.get();
    }

    /**
     * 在当前线程的 trace 上开一个计时阶段。
     *
     * <p>没有开 trace 时返回空实现而不是 null——这样埋点代码可以无脑写成
     * {@code try (Span s = TraceContext.span(...))}，不必每层都判空，
     * 也不会因为漏判而把 NPE 甩进主流程。
     */
    public static Span span(String name, String kind) {
        Trace t = CURRENT.get();
        return t == null ? Span.noop() : t.span(name, kind);
    }

    /** 清理当前线程的 trace，必须在 finally 中调用。 */
    public static void clear() {
        CURRENT.remove();
    }

    static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    /** 内部阶段。用 try-with-resources 包裹即可自动记录耗时。 */
    public static final class Span implements AutoCloseable {
        private final Trace owner;
        private final long startNanos;
        /** 相对 trace 起点的偏移；同包的 recorder 落库时要读，故不用 private */
        final long startOffsetMs;
        final String name;
        final String kind;
        String detail;
        long durationMs = -1;

        Span(Trace owner, String name, String kind) {
            this.owner = owner;
            this.name = name;
            this.kind = kind;
            this.startNanos = System.nanoTime();
            this.startOffsetMs = owner == null ? 0 : owner.offsetMs();
        }

        /** 补充阶段信息（如命中的文档 id），返回自身便于链式调用。 */
        public Span detail(String text) {
            if (text != null && !text.isBlank()) {
                this.detail = text.length() > 500 ? text.substring(0, 500) : text;
            }
            return this;
        }

        @Override
        public void close() {
            if (owner != null) {
                owner.record(this, (System.nanoTime() - startNanos) / 1_000_000L);
            }
        }

        static Span noop() {
            return new Span(null, "noop", "noop");
        }
    }

    /** 一次调用的完整记录。 */
    public static final class Trace {
        private final String traceId;
        private final String channel;
        private final long startNanos;
        private final List<Span> spans = new ArrayList<>();

        public String sessionKey;
        public String ownerType;
        public String ownerId;
        public String intent;
        public String model;
        public String promptVersion;
        public String status = "OK";
        public String errorType;
        public boolean degraded;
        public Integer promptTokens;
        public Integer completionTokens;
        public Integer totalTokens;
        public Integer hitCount;
        public Double topScore;
        public String userText;
        /**
         * 置位后本次不落库。用于"根本没进路由"的请求（如只有问候语、无用户发言）——
         * 那不算一次智能体调用，计进去只会虚增调用量、并把延迟分位拉向 0。
         */
        public boolean discard;

        Trace(String traceId, String channel) {
            this.traceId = traceId;
            this.channel = channel;
            this.startNanos = System.nanoTime();
        }

        /** 开启一个计时阶段；无 trace 时返回空实现，调用方无需判空。 */
        public Span span(String name, String kind) {
            return new Span(this, name, kind);
        }

        long offsetMs() {
            return (System.nanoTime() - startNanos) / 1_000_000L;
        }

        long latencyMs() {
            return offsetMs();
        }

        void record(Span span, long durationMs) {
            span.durationMs = durationMs;
            synchronized (spans) {
                if (spans.size() < 64) {   // 防御：异常路径下别把内存撑爆
                    spans.add(span);
                }
            }
        }

        /** 各阶段耗时之和（毫秒），用于前端画瀑布图。 */
        public List<Object[]> spanRows() {
            List<Object[]> rows = new ArrayList<>();
            synchronized (spans) {
                for (Span s : spans) {
                    rows.add(new Object[]{s.name, s.kind, s.startOffsetMs, s.durationMs, s.detail});
                }
            }
            return rows;
        }

        public String traceId() {
            return traceId;
        }

        public String channel() {
            return channel;
        }

        public List<Span> spans() {
            synchronized (spans) {
                return new ArrayList<>(spans);
            }
        }

        /** 标记本次走的是降级路径（如存储/大模型不可用但仍给了回复）。 */
        public void degraded(String reason) {
            this.degraded = true;
            this.errorType = reason;
            if ("OK".equals(this.status)) {
                this.status = "DEGRADED";
            }
        }

        public void failed(String errorType) {
            this.status = "ERROR";
            this.errorType = errorType;
        }
    }
}
