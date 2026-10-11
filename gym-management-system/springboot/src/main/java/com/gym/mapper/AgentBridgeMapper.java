package com.gym.mapper;

import com.gym.entity.Recharge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 智能体桥接专用 Mapper。
 *
 * <p>刻意与原有的 MemberMapper / RechargeMapper 分开：这里只放「对话式充值闭环」
 * 需要的几条语句，不修改也不依赖任何既有语句。这样业务系统原有逻辑一行未动，
 * 出问题删掉本文件即可完全回滚。
 *
 * <p>设计上复用了既有的 {@code recharge} 表来充当订单表：
 * 它的 {@code rechargeStatus} 天然就有「未完成 / 成功」两态，
 * {@code rechargeNo} 又是自增主键，正好当订单号，因此不需要新建任何表。
 */
@Mapper
@Repository
public interface AgentBridgeMapper {

    /** 是否真的存在这个会员，避免为无效会员开出订单 */
    int countMember(@Param("memberNo") int memberNo);

    /** 余额（元） */
    Integer getMemberChange(@Param("memberNo") int memberNo);

    /** 余额增减。delta 为正是充值入账，为负是扣费 */
    int addMemberChange(@Param("memberNo") int memberNo, @Param("delta") int delta);

    /** 创建待支付订单：插入一条 rechargeStatus = 0 的流水，自增主键回填到 rechargeNo */
    int insertPendingRecharge(Recharge recharge);

    /** 支付成功：仅当仍是待支付时才更新，保证重复回调不会重复入账 */
    int markRechargePaid(@Param("rechargeNo") int rechargeNo);

    /** 订单状态：0 待支付 / 1 已支付 */
    Integer getRechargeStatus(@Param("rechargeNo") int rechargeNo);

    Recharge getRechargeById(@Param("rechargeNo") int rechargeNo);
}
