package com.gym.service;


import com.gym.entity.Recharge;
import com.gym.mapper.RechargeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RechargeService {

    @Autowired
    private RechargeMapper rechargeMapper;

    public List<Recharge> getRechargeByMemberNo(int memberNo){
        return rechargeMapper.getRechargeByMemberNo(memberNo);
    }


    public Map<String,Object> addRechargeByMemberNo(Recharge recharge) {

        //充值时间
        recharge.setRechargeDate(LocalDate.now());
        System.out.println(LocalDate.now());

        recharge.setRechargeMethod("在线充值");
        recharge.setRechargeStatus(1);

        Map<String,Object> resultMap = new HashMap<>();

        int result = rechargeMapper.addRechargeByMemberNo(recharge);

        if(result>0){
            resultMap.put("code",200);
            resultMap.put("message","充值成功");
        }else{
            resultMap.put("code",400);
            resultMap.put("message","充值失败");
        }

        return resultMap;
    }

}
