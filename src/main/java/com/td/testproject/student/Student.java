package com.td.testproject.student;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("students")
@Data
public class Student {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id
    private String id;
    private String name;
    private String email;
    private Integer age;

    public void updateStudent(Student newStudent) {
        this.name = newStudent.getName();
        this.email = newStudent.getEmail();
        this.age = newStudent.getAge();
    }
}
