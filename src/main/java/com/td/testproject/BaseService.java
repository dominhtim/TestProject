package com.td.testproject;

import com.td.testproject.student.Student;
import java.util.List;

public interface BaseService {

    List<Student> getStudents();

    Student getStudent(String id);

    Student addStudent(Student student);

    void deleteStudent(String id);

    Student updateStudent(String id, Student student);
}
