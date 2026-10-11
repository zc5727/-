package com.gym.mapper;

import com.gym.entity.Recharge;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;


@Mapper
@Repository
public interface RechargeMapper {

    List<Recharge> getRechargeByMemberNo(int memberNo);

    int addRechargeByMemberNo(Recharge recharge);
}
