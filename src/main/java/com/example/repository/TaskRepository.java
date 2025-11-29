package com.example.repository;

import com.example.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Task entity.
 * JpaRepository provides all standard CRUD operations (Create, Read, Update, Delete)
 * out of the box without needing to write any implementation code.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    // Custom methods can be defined here, but standard CRUD is inherited.
}
