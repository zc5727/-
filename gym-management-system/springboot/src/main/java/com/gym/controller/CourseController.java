package com.gym.controller;

import com.gym.entity.Common;
import com.gym.entity.Course;
import com.gym.entity.Equipment;
import com.gym.service.AdminService;
import com.gym.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class CourseController {
    @Autowired
    private CourseService courseService;

    @RequestMapping(path= "/getAllCourse")
    public List<Course> getAllCourse(int page, int size){
        return courseService.getAllCourse(page,size);
    }

    @RequestMapping(path= "/getAllCourseRegister")
    public List<Course> getAllCourseRegister(){
        return courseService.getAllCourseRegister();
    }

    @RequestMapping(path= "/addCourse")
    public Map<String,Object> addCourse(@RequestBody Course course){
        return courseService.addCourse(course);
    }

    @RequestMapping(path ="/updateCourse")
    public Map<String,Object> updateEquipment(@RequestBody Course course){
        return courseService.updateCourse(course);
    }

    @RequestMapping(path ="/deleteCourse")
    public Map<String,Object> deleteEmployee(int courseNo){
        return courseService.deleteCourse(courseNo);
    }

    @RequestMapping(path = "/totalCourse")
    public Common totalCourse() {
        return courseService.totalCourse();
    }

    /**
     * 模糊搜索接口
     * @param keyWord 前端传过来的模糊搜索 关键词
     * @param page 第几页
     * @param size 一页的大小
     * @return 返回查到的课程的列表
     */
    @RequestMapping(path = "/getByKeywordCourse")
    public List<Course> getByKeywordCourse(String keyWord, int page, int size){
        return courseService.getByKeywordCourse(keyWord,page,size);
    }

    @RequestMapping(path = "/totalCourseFuzzy")
    public Common totalCourseFuzzy(String keyWord) {
        return courseService.totalCourseFuzzy(keyWord);
    }

}
