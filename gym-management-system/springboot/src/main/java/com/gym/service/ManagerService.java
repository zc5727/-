package com.gym.service;

import com.gym.entity.Employee;
import com.gym.entity.Manager;
import com.gym.mapper.ManagerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ManagerService {

    @Autowired
    private ManagerMapper managerMapper;

    public List<Manager> getAllManager() {
        return managerMapper.getAllManager();
    }
}
