package com.gym.mapper;



import com.gym.entity.Common;
import com.gym.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface CourseMapper {
    List<Course> getAllCourse(int page, int size);

    List<Course> getAllCourseRegister();

    int addCourse(Course course);

    double getCoursePriceByCourseNo(int CourseNo);

    int updateCourse(Course course);

    int deleteCourse(int courseNo);

    Common totalCourse();

    List<Course> getByKeywordCourse(String keyWord,int page,int size);

    Common totalCourseFuzzy(String keyWord);
}
