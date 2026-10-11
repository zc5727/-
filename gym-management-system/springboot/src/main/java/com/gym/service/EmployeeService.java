package com.gym.service;

import com.gym.entity.Common;
import com.gym.entity.Employee;
import com.gym.mapper.EmployeeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmployeeService {
    @Autowired
    private EmployeeMapper employeeMapper;

    public List<Employee> getAllEmployee(int page, int size){
        return employeeMapper.getAllEmployee(page,size);
    }

    public Map<String,Object> addEmployee(Employee employee){
        Map<String,Object> resultMap = new HashMap<>();

        /*设置入职时间*/
        employee.setEmployeeTime(LocalDateTime.now());

        int result =  employeeMapper.addEmployee(employee);



        if(result>0){
            resultMap.put("code",200);
            resultMap.put("message","添加成功");
        }else{
            resultMap.put("code",400);
            resultMap.put("message","添加失败");
        }

        return resultMap;
    }

    public Map<String,Object>  updateEmployee(Employee employee) {
        Map<String,Object> resultMap = new HashMap<>();
        int result =  employeeMapper.updateEmployee(employee);

        if(result>0){
            resultMap.put("code",200);
            resultMap.put("message","修改成功");
        }else{
            resultMap.put("code",400);
            resultMap.put("message","修改失败");
        }

        return resultMap;
    }

    public Map<String,Object>  deleteEmployee(int employeeNo) {
        Map<String,Object> resultMap = new HashMap<>();
        int result =  employeeMapper.deleteEmployee(employeeNo);

        if(result>0){
            resultMap.put("code",200);
            resultMap.put("message","删除成功");
        }else{
            resultMap.put("code",400);
            resultMap.put("message","删除失败");
        }

        return resultMap;
    }

    public Common totalEmployee() {
        return employeeMapper.totalEmployee();
    }

    public List<Employee> getByKeywordEmployee(String keyWord,int page,int size) {
        return  employeeMapper.getByKeywordEmployee(keyWord,page,size);
    }

    public Common totalEmployeeFuzzy(String keyWord){
        return employeeMapper.totalEmployeeFuzzy(keyWord);
    }

    public List<Employee> getAllCoach() {
        return employeeMapper.getAllCoach();
    }

    public List<Employee> getAllManager() {
        return employeeMapper.getAllManager();
    }
}


