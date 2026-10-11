package com.gym.controller;
//1

import com.gym.service.AdminService;
import com.gym.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class AdminController {


    @Autowired
    private AdminService adminService;

    @RequestMapping(path="/getAdminPassword")
    @CrossOrigin
    public Map<String,Object> getAdminPassword(String adminAccount,String adminPassword ) {
        return adminService.getAdminPassword(adminAccount,adminPassword);
    }

    @RequestMapping(path="/checkToken")
    @CrossOrigin
    public Boolean checkToken(HttpServletRequest request) {
        String token = request.getHeader("token");
        return JwtUtil.checkToken(token);
    }
}
