package com.example.repository;

import com.example.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Standard CRUD repository for Task, provided entirely by JpaRepository.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
}
