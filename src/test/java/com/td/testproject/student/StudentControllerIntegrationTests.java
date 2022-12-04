package com.td.testproject.student;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StudentControllerIntegrationTests {

    @Autowired
    private StudentRepository studentRepository;

    @BeforeEach
    void setup() {
        studentRepository.deleteAll();
    }

    @AfterEach
    void shutdown() {
        studentRepository.deleteAll();
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void addStudentTest() {
        Student student = Student.builder()
            .name("Ramesh")
            .email("ramesh@gmail.com")
            .age(23)
            .build();
        ResponseEntity<Student> response = restTemplate.postForEntity("/api/v1/student/",
            student, Student.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(student);
    }
}