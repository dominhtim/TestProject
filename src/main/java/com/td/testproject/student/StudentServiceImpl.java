package com.td.testproject.student;

import com.td.testproject.BaseService;
import com.td.testproject.Validator;
import java.util.List;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("student")
@Log4j2
public class StudentServiceImpl implements BaseService {

    private StudentRepository studentRepository;
    private Validator studentValidator;

    @Autowired
    public void setStudentRepository(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Autowired
    public void setStudentValidator(Validator studentValidator) {
        this.studentValidator = studentValidator;
    }

    @Override
    public List<Student> getStudents() {
        return studentRepository.findAll();
    }

    @Override
    public Student getStudent(@NonNull String id) {
        List<Student> students = studentRepository.findByStudentId(id);
        studentValidator.validateForEmpty(students, id);
        return students.stream()
            .findFirst()
            .orElse(null);
    }

    @Override
    public Student addStudent(@NonNull Student student) {
        List<Student> foundStudents = studentRepository.findByEmail(student.getEmail());
        studentValidator.validateForNonEmpty(foundStudents, student.getEmail());
        studentRepository.save(student);
        return student;
    }

    @Override
    public void deleteStudent(@NonNull String id) {
        Student student = getStudent(id);
        studentRepository.delete(student);
    }

    @Override
    public Student updateStudent(@NonNull String id, @NonNull Student student) {
        List<Student> students = studentRepository.findByStudentId(id);
        studentValidator.validateForEmpty(students, id);
        student.setId(id);
        studentRepository.save(student);
        return student;
    }
}
