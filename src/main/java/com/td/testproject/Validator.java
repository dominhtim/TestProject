package com.td.testproject;

import com.td.testproject.student.Student;
import java.util.List;

public interface Validator {

    void validateForEmpty(List<Student> students, String id);

    void validateForNonEmpty(List<Student> students, String email);
}
