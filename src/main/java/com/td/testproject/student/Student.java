package com.td.testproject.student;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("students")
@Data
@Builder
public class Student {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id
    private String id;
    private String name;
    private String email;
    private Integer age;
}
