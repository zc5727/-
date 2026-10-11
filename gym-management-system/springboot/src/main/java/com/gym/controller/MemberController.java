package com.gym.controller;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.gym.entity.Common;
import com.gym.entity.Member;
import com.gym.service.MemberService;
import com.gym.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
public class MemberController {

    @Autowired
    private MemberService memberService;

    /**
     * 获取所有会员信息
     * @param page 页码
     * @param size 每页显示的数量
     * @return 会员信息
     */
    @RequestMapping(path="/getAllMember")
    public List<Member> getAllMember(int page,int size){
        return memberService.getMemberMapper(page,size);
    }

    /**
     * 新增会员
     * @param member 会员表单
     * @return map
     */
    @RequestMapping(path = "/addMember")
    public Map<String,Object> addMember(Member member) {
        return memberService.addMember(member);
    }


    /**
     * 新增会员
     * @param member 会员表单
     * @return map
     */
    @PostMapping(path = "/register")
    public Map<String,Object> register(@RequestBody Member member) {
        return memberService.register(member);
    }


    /**
     * 删除用户
     * @param memberNo 用户编号
     * @return map
     */
    @RequestMapping(path="/deleteMember")
    public Map<String,Object> deleteMember(int memberNo) {
        return memberService.deleteMember(memberNo);
    }


    /**
     * 更新用户
     * @param member 会员表单
     * @return map
     */
    @RequestMapping(path="/updateMember")
    public Map<String,Object> updateMember(Member member) {
        return memberService.updateMember(member);
    }

    /**
     * 通过会员编号更新会员
     * @param member
     * @return
     */
    @RequestMapping(path="/updateMemberByMemberNo")
    public Map<String,Object> updateMemberByMemberNo(Member member) {
        return memberService.updateMemberByMemberNo(member);
    }

    /**
     * 查找会员表数据总条数
     * @return dataTotal
     */
    @RequestMapping(path = "/totalMember")
    public Common totalMember() {
        return memberService.totalMember();
    }

    /**
     * 登录
     * @param memberPhone 手机号
     * @param memberPassword 密码
     * @return
     */
    @RequestMapping(path="/getMemberPassword")
    @CrossOrigin
    public Map<String,Object> getMemberPassword(String memberPhone,String memberPassword){
        return memberService.getMemberPassword(memberPhone,memberPassword);
    }

    /**
     * 模糊查询用户
     * @param keyWord 关键词
     * @param page 页码
     * @param size 每页显示的数量
     * @return 会员列表
     */
    @RequestMapping(path = "/getByKeywordMember")
        public List<Member> getByKeywordMember(String keyWord,int page,int size){
            return memberService.getByKeywordMember(keyWord,page,size);
    }

    /**
     * 模糊查询用户总数
     * @param keyWord 关键词
     * @return
     */
    @RequestMapping(path = "/totalMemberFuzzy")
    public Common totalMemberFuzzy(String keyWord) {
        return memberService.totalMemberFuzzy(keyWord);
    }

    /**
     * 获取会员积分
     */
    @RequestMapping(path = "/getMemberIntegral")
    public double getMemberIntegral(int memberNo) {
        return memberService.getMemberIntegral(memberNo);
    }

    /**
     * 获取会员余额
     */
    @ResponseBody
    @RequestMapping(path = "/getMemberChange")
    public double getMemberChange(int memberNo) {
        return memberService.getMemberChange(memberNo);
    }

    /**
     * 通过会员编号获取用户信息
     */
    @RequestMapping(path = "/getMemberByMemberNo")
    public Member getMemberByMemberNo(Integer memberNo) {
        return memberService.getMemberByMemberNo(memberNo);
    }

    /**
     * 获取会员充值总额
     */
    @RequestMapping(path = "/getTotalMoney")
    public Double getTotalMoney(Integer memberNo) {
        return memberService.getTotalMoney(memberNo);
    }

    /**
     * 获取会员权限
     */
    @RequestMapping(path = "/getMemberPower")
    public double getMemberPower(Integer memberNo) {
        return memberService.getMemberPower(memberNo);
    }

    /**
     * 更新会员额度
     */
    @RequestMapping(path = "/updateMemberChange")
    public double updateMemberChange(Integer memberNo) {
        return memberService.updateMemberChange(memberNo);
    }

    /**
     * 更新会员积分
     */
    @RequestMapping(path = "/updateMemberIntegral")
    public Map<String,Object> updateMemberIntegral(Double price,Integer memberNo) {
        return memberService.updateMemberIntegral(price,memberNo);
    }

    /**
     * 更新会员余额
     */
    @RequestMapping(path = "/updateMemberChangeByMemberNo")
    public Map<String,Object> updateMemberChangeByMemberNo(int memberNo,double coursePrice) {
        return memberService.updateMemberChangeByMemberNo(memberNo,coursePrice);
    }

    /**
     * 修改会员密码
     */
    @RequestMapping(path = "/updateMemberPassword")
    public Map<String,Object> updateMemberPassword(String memberPhone,String memberPassword) {
        return memberService.updateMemberPassword(memberPhone,memberPassword);
    }

    /**
     * 修改会员权限
     */
    @RequestMapping(path = "/updateMemberPower")
    public Map<String,Object> updateMemberPower(int memberPower,int memberNo) {
        return memberService.updateMemberPower(memberPower,memberNo);
    }

    /**
     * 导出接口
     */
    @GetMapping("/memberExport")
    public void export(HttpServletResponse response) throws Exception {
        // 从数据库查询出所有的数据
        List<Member> list = memberService.getAllMemberNoPage();
        // 通过工具类创建writer 写出到磁盘路径
        ExcelWriter writer = ExcelUtil.getWriter(true);

        // 一次性写出list内的对象到excel，使用默认样式，强制输出标题
        writer.write(list, true);

        // 设置浏览器响应的格式
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("会员信息", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        ServletOutputStream out = response.getOutputStream();
        writer.flush(out, true);
        out.close();
        writer.close();

    }


    /**
     * excel 导入
     * @param file
     * @throws Exception
     */
    @RequestMapping("/memberImport")
    @CrossOrigin
    public Map<String, Object> imp(MultipartFile file) throws Exception {
        InputStream inputStream = file.getInputStream();
        ExcelReader reader = ExcelUtil.getReader(inputStream);

        List<Member> list = reader.readAll(Member.class);

        System.out.println(list);

        //上传的excel数据,保存到数据库中
        Map<String, Object> stringObjectMap = memberService.saveMemberBatch(list);
        return stringObjectMap;
    }

}
