package com.td.testproject.student;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface StudentRepository extends MongoRepository<Student, Long> {

    @Query("{ 'id' : ?0 }")
    List<Student> findByStudentId(String id);

    @Query("{ 'email' : ?0 }")
    List<Student> findByEmail(String email);
}
