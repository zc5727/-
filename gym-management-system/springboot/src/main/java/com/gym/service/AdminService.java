package com.gym.service;

import com.gym.entity.Admin;
import com.gym.entity.Common;
import com.gym.entity.Member;
import com.gym.mapper.AdminMapper;
import com.gym.mapper.MemberMapper;
import com.gym.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    @Autowired
    private AdminMapper adminMapper;


    public Map<String,Object> getAdminPassword(String adminAccount,String adminPassword){
        Map<String,Object> resultMap = new HashMap<>();
        Admin result = adminMapper.getAdminPassword(adminAccount,adminPassword);

        if(result != null){
            result.setToken(JwtUtil.createToken(adminAccount));
            resultMap.put("token",result.getToken());
            resultMap.put("adminAccount",adminAccount);
            resultMap.put("code",200);
            resultMap.put("message","登录成功");

        }else{
            resultMap.put("code",400);
            resultMap.put("message","登录失败");
            resultMap.put("token",null);
        }
        return resultMap;
    }

}
