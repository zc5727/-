package com.gym.controller;

import com.gym.entity.Employee;
import com.gym.entity.Manager;
import com.gym.service.ManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ManagerController {

    @Autowired
    private ManagerService managerService;

    @GetMapping("/getAllManager")
    public List<Manager> getAllManager() {
        return managerService.getAllManager();
    }
}
