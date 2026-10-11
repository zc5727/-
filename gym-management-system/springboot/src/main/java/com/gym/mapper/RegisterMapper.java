package com.gym.mapper;


import com.gym.entity.Common;
import com.gym.entity.Member;
import com.gym.entity.Register;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface RegisterMapper {
    List<Register> getAllRegister(int page,int size);

    List<Register> getRegisterByMemberNo(int memberNo);

    Member getMemberByPhone(String memberPhone);

    int addRegister(Register register);

    int updateRegister(Register register);

    int deleteRegister(int registerNo);

    Common totalRegister();

    List<Register> getByKeywordRegister(String keyWord,int page,int size);

    Common totalRegisterFuzzy(String keyWord);

    Register checkRegister(int courseNo ,int memberNo);

    Double getTotalBuyByMemberNo(int memberNo);

//    //柱状图--课程种类接口
//    List<Register> getAllCourseDuration(int courseDuration);
}
