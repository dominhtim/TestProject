package com.td.testproject.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.td.testproject.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StudentServiceTest {

    @Mock
    private StudentRepository mockStudentRepository;
    @Mock
    private Validator mockStudentValidator;

    @InjectMocks
    private StudentServiceImpl service;

    @Test
    public void testGetStudents() {
        Mockito.when(mockStudentRepository.findAll()).thenReturn(new ArrayList<>());
        List<Student> studentList = service.getStudents();
        assertThat(studentList).isEmpty();

        List<Student> mockStudentList = createMockStudentList();
        Mockito.when(mockStudentRepository.findAll()).thenReturn(mockStudentList);
        studentList = service.getStudents();
        assertThat(studentList).isEqualTo(mockStudentList);
    }

    private List<Student> createMockStudentList() {
        List<Student> studentList = new ArrayList<>();
        for (int i = 0; i < 10; i += 1) {
            Random random = new Random();
            Student student = Student.builder()
                .id(UUID.randomUUID().toString())
                .name(UUID.randomUUID().toString())
                .email(UUID.randomUUID().toString())
                .age(random.nextInt(99))
                .build();
            studentList.add(student);
        }
        return studentList;
    }
}
