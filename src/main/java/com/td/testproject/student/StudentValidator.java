package com.td.testproject.student;

import com.td.testproject.Validator;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
public class StudentValidator implements Validator {

    public void validateForEmpty(List<Student> students, String id) {
        if (CollectionUtils.isEmpty(students)) {
            throw new RuntimeException("No Student with Id: " + id);
        }
    }

    @Override
    public void validateForNonEmpty(List<Student> students, String email) {
        if (CollectionUtils.isNotEmpty(students)) {
            throw new RuntimeException("Student email exists: " + email);
        }
    }
}
