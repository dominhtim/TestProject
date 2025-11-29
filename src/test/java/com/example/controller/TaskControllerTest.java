package com.example.controller;

import com.example.model.Task;
import com.example.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Integration Test for the TaskController.
 * This test uses the full Spring context and a real H2 in-memory database,
 * providing comprehensive CRUD coverage.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    private static final String API_V1_TASKS = "/api/v1/tasks";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Clear the database before each test
    @BeforeEach
    void setup() {
        taskRepository.deleteAll();
    }

    // --- CREATE & READ ALL TESTS ---

    @Test
    void shouldCreateAndRetrieveTask() throws Exception {
        // 1. CREATE Task
        Task newTask = new Task(null, "Buy groceries", false);
        String taskJson = objectMapper.writeValueAsString(newTask);

        // Perform POST request
        mockMvc.perform(post(API_V1_TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(jsonPath("$.title", is("Buy groceries")))
                .andExpect(jsonPath("$.id").isNumber());

        // 2. READ ALL Tasks
        mockMvc.perform(get(API_V1_TASKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void shouldReturnBadRequestOnCreateWithMissingTitle() throws Exception {
        // Task with missing (blank) title will fail @NotBlank validation
        Task invalidTask = new Task(null, " ", false);
        String invalidJson = objectMapper.writeValueAsString(invalidTask);

        // Perform POST request
        mockMvc.perform(post(API_V1_TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest()); // Expect 400 Bad Request
    }

    // --- READ ONE TESTS ---

    @Test
    void shouldGetTaskById() throws Exception {
        // SETUP: Create a task
        Task savedTask = taskRepository.save(new Task(null, "Task to read", false));

        // Perform GET request by ID
        mockMvc.perform(get(API_V1_TASKS + "/{id}", savedTask.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Task to read")));
    }

    @Test
    void shouldReturn404WhenTaskNotFound() throws Exception {
        // Perform GET request for a non-existent ID
        mockMvc.perform(get(API_V1_TASKS + "/{id}", 999L))
                .andExpect(status().isNotFound()); // Expect 404 Not Found
    }

    // --- UPDATE TESTS ---

    @Test
    void shouldUpdateTask() throws Exception {
        // SETUP: Create a task first
        Task savedTask = taskRepository.save(new Task(null, "Old Title", false));

        // Task update details
        Task updateDetails = new Task(savedTask.getId(), "New Updated Title", true);
        String updateJson = objectMapper.writeValueAsString(updateDetails);

        // Perform PUT request
        mockMvc.perform(put(API_V1_TASKS + "/{id}", savedTask.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("New Updated Title")))
                .andExpect(jsonPath("$.completed", is(true)));
    }

    @Test
    void shouldReturn404OnUpdateIfTaskNotFound() throws Exception {
        // Task update details for a non-existent ID
        Task updateDetails = new Task(999L, "Non-existent update", true);
        String updateJson = objectMapper.writeValueAsString(updateDetails);

        // Perform PUT request
        mockMvc.perform(put(API_V1_TASKS + "/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }

    // --- DELETE TESTS ---

    @Test
    void shouldDeleteTask() throws Exception {
        // SETUP: Create a task first
        Task savedTask = taskRepository.save(new Task(null, "Task to delete", false));

        // Perform DELETE request
        mockMvc.perform(delete(API_V1_TASKS + "/{id}", savedTask.getId()))
                .andExpect(status().isNoContent()); // Expect 204 No Content

        // Verify task is actually deleted (READ ONE should return 404)
        mockMvc.perform(get(API_V1_TASKS + "/{id}", savedTask.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404OnDeleteIfTaskNotFound() throws Exception {
        // Perform DELETE request for a non-existent ID
        mockMvc.perform(delete(API_V1_TASKS + "/{id}", 999L))
                .andExpect(status().isNotFound()); // Expect 404 Not Found
    }
}
