package com.hbjt.fitness.agent.security;

/**
 * 会话归属（数据隔离的锚点）。
 *
 * <p>三种归属，来源不同，可信度也不同——这一点必须写在类型上，而不是靠注释提醒：
 * <ul>
 *   <li>{@code member}：会员编号来自业务系统对登录令牌的解析结果，<b>服务端可验证</b>；
 *       会员之间互相看不到对方的会话。</li>
 *   <li>{@code admin}：管理员账号同样来自令牌解析。</li>
 *   <li>{@code guest}：未登录访客。身份不可验证，用浏览器本地生成的一个随机标识区分，
 *       因此它的隔离范围是「同一浏览器的同一份本地存储」，而不是「某个人」。
 *       不把它与登录身份混为一谈，是为了不给人"这是账号级隔离"的错觉。</li>
 * </ul>
 *
 * @param type       member / admin / guest
 * @param id         归属标识：会员编号 / 管理员账号 / 浏览器标识
 * @param memberNo   会员编号（仅会员归属非空）
 * @param verified   身份是否经服务端验证（guest 为 false）
 * @param label      展示用名称
 */
public record OwnerContext(
        String type,
        String id,
        Long memberNo,
        boolean verified,
        String label
) {

    public static final String MEMBER = "member";
    public static final String ADMIN = "admin";
    public static final String GUEST = "guest";

    public static OwnerContext member(long memberNo, String label) {
        return new OwnerContext(MEMBER, String.valueOf(memberNo), memberNo, true,
                label == null || label.isBlank() ? "会员 " + memberNo : label);
    }

    public static OwnerContext admin(String account) {
        return new OwnerContext(ADMIN, account, null, true,
                account == null || account.isBlank() ? "管理员" : account);
    }

    public static OwnerContext guest(String clientId) {
        return new OwnerContext(GUEST, clientId, null, false, "访客");
    }

    public boolean isMember() {
        return MEMBER.equals(type);
    }

    /** 供日志使用：不打印完整 id（会话标识类数据不必要地落到日志里没有好处） */
    public String describe() {
        String shortId = id == null ? "-" : (id.length() <= 6 ? id : id.substring(0, 6) + "…");
        return type + ":" + shortId + (verified ? "" : "(未验证)");
    }
}
