package com.gym.mapper;

import com.gym.entity.Manager;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ManagerMapper {
    List<Manager> getAllManager();


}
