package com.hbjt.fitness.agent.domain;

/**
 * 对话意图。
 *
 * <p>分四层，路由时严格按层判断：
 * <ol>
 *   <li><b>等待态短答</b>：CONFIRM / CANCEL —— 仅在上一轮留下了待办动作时才有意义</li>
 *   <li><b>查询类</b>：QUERY_* —— 只读，优先级高于动作类。其中 QUERY_KNOWLEDGE
 *       是"健身知识"而不是"我的数据"，它回答的是通用问题（动作怎么做、营养怎么配），
 *       与会员本人账户无关，因此不需要会员身份。</li>
 *   <li><b>动作类</b>：RECHARGE / FITNESS_PLAN —— 会改数据或产生新内容</li>
 *   <li><b>HELP / UNKNOWN</b> —— 兜底，绝不猜</li>
 * </ol>
 * 第 2 层高于第 3 层是刻意的：「充值记录」必须被识别成查询，
 * 而不能因为里面有一个「充值」就被当成发起充值。
 */
public enum Intent {

    /* ---------- 等待态短答 ---------- */
    CONFIRM,
    CANCEL,

    /* ---------- 查询类（只读） ---------- */
    QUERY_BALANCE,
    QUERY_INTEGRAL,
    QUERY_RECHARGE,
    QUERY_COURSE,
    /** 健身知识问答：训练动作 / 营养 / 安全，走知识库检索 */
    QUERY_KNOWLEDGE,

    /* ---------- 动作类 ---------- */
    RECHARGE,
    FITNESS_PLAN,
    REGISTER_COURSE,
    CHECKIN,

    /* ---------- 兜底 ---------- */
    HELP,
    UNKNOWN;

    public boolean isQuery() {
        return this == QUERY_BALANCE || this == QUERY_INTEGRAL
                || this == QUERY_RECHARGE || this == QUERY_COURSE
                || this == QUERY_KNOWLEDGE;
    }
}
