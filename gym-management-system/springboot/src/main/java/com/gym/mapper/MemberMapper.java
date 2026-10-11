package com.gym.mapper;

import com.gym.entity.Common;
import com.gym.entity.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import javax.xml.crypto.Data;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Mapper
@Repository
public interface MemberMapper {

     List<Member> getAllMember(int page,int size);

     /*不分页*/
     List<Member> getAllMemberNoPage();

     int saveMemberBatch(@Param("list") List<Member> list);

     int addMember(Member member);

     int deleteMember(int memberNo);

     int updateMember(Member member);

     int updateMemberByMemberNo(Member member);

     Common totalMember();

     List<Member> getByKeywordMember(String keyWord,int page,int size);

     Common totalMemberFuzzy(String keyWord);

     Member getMemberPassword(String memberPhone,String memberPassword);

     Member getMemberByMemberNo(int memberNo);

     double getMemberIntegral(int memberNo);

     double getMemberChange(int memberNo);

     Double getTotalMoney(int memberNo);

     int updateMemberChange(int memberNo,double totalBuy);

     int updateMemberIntegral(double price,int memberNo);

     int updateMemberPassword(String memberPhone,String memberPassword);

     int updateMemberPower(int memberPower ,int memberNo);

     int getMemberPower(int memberNo);

     int updateMemberChangeByMemberNo(int memberNo,double coursePrice);


     Integer getMemberBySeason(Date startTime, Date endTime);

    Integer getSexByYear(String sex, Integer year);

    Member getMemberByPhone(String memberPhone);

}
