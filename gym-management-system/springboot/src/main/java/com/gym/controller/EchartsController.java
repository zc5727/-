package com.gym.controller;

import com.gym.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/echarts")
public class EchartsController {

    @Resource
    private MemberService memberService;


    @GetMapping("/getMemberBySeason")
    public Map<String, Object> getMemberBySeason(@RequestParam("year") Integer year) {
        return memberService.getMemberBySeason(year);
    }

    @GetMapping("/getMemberSexByYear")
    public Map<String, Object> getMemberSexByYear(@RequestParam("year") Integer year) {
        return memberService.getMemberSexByYear(year);
    }
}
