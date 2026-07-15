package com.example.controller;

import com.example.model.Task;
import com.example.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit Test for the TaskController.
 * Uses @WebMvcTest to load only the controller and mock its dependencies (TaskRepository),
 * making these tests fast and isolated. This ensures 100% coverage of the controller logic.
 */
@WebMvcTest(TaskController.class)
class TaskControllerUnitTest {

    private static final String API_V1_TASKS = "/api/v1/tasks";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock the TaskRepository dependency
    @MockitoBean
    private TaskRepository taskRepository;

    // Test data
    private final Task task1 = new Task(1L, "Unit Test Task 1", false);
    private final Task task2 = new Task(2L, "Unit Test Task 2", true);

    // --- CREATE TESTS (POST) ---

    @Test
    void shouldCreateTask() throws Exception {
        // Given: Mock the save operation to return the provided task (with an ID)
        when(taskRepository.save(any(Task.class))).thenReturn(task1);

        // When/Then: Perform POST request
        mockMvc.perform(post(API_V1_TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Unit Test Task 1")));

        // Verify the repository save method was called exactly once
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void shouldReturnBadRequestOnCreateIfTitleIsMissing() throws Exception {
        // Task with missing (blank) title should fail validation
        Task invalidTask = new Task(null, " ", false);

        // When/Then: Perform POST request
        mockMvc.perform(post(API_V1_TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTask)))
                .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // Verify that the repository save method was never called
        verify(taskRepository, never()).save(any(Task.class));
    }

    // --- READ ALL TESTS (GET /) ---

    @Test
    void shouldGetAllTasks() throws Exception {
        // Given: Mock findAll to return a list of tasks
        when(taskRepository.findAll()).thenReturn(Arrays.asList(task1, task2));

        // When/Then: Perform GET request
        mockMvc.perform(get(API_V1_TASKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is(task1.getTitle())));

        // Verify findAll was called
        verify(taskRepository, times(1)).findAll();
    }

    // --- READ ONE TESTS (GET /id) ---

    @Test
    void shouldGetTaskById() throws Exception {
        // Given: Mock findById to return the task
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));

        // When/Then: Perform GET request
        mockMvc.perform(get(API_V1_TASKS + "/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is(task1.getTitle())));

        // Verify findById was called
        verify(taskRepository, times(1)).findById(1L);
    }

    @Test
    void shouldReturn404IfTaskNotFound() throws Exception {
        // Given: Mock findById to return empty
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then: Perform GET request
        mockMvc.perform(get(API_V1_TASKS + "/{id}", 999L))
                .andExpect(status().isNotFound());

        // Verify findById was called
        verify(taskRepository, times(1)).findById(999L);
    }

    // --- UPDATE TESTS (PUT) ---

    @Test
    void shouldUpdateTask() throws Exception {
        // Mock data for the update
        Task updatedDetails = new Task(1L, "Updated Title", true);

        // 1. Mock findById (The task exists)
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));
        // 2. Mock save (The task is updated and saved)
        when(taskRepository.save(any(Task.class))).thenReturn(updatedDetails);

        // When/Then: Perform PUT request
        mockMvc.perform(put(API_V1_TASKS + "/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.completed", is(true)));

        // Verify calls
        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void shouldReturn404OnUpdateIfTaskNotFound() throws Exception {
        // Mock data for the update
        Task updatedDetails = new Task(999L, "Non-existent", true);

        // 1. Mock findById (The task is not found)
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then: Perform PUT request
        mockMvc.perform(put(API_V1_TASKS + "/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedDetails)))
                .andExpect(status().isNotFound());

        // Verify only findById was called
        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).save(any(Task.class));
    }

    // --- DELETE TESTS (DELETE) ---

    @Test
    void shouldDeleteTask() throws Exception {
        // 1. Mock findById (The task exists)
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));
        // 2. Mock the delete operation (void method)
        doNothing().when(taskRepository).delete(any(Task.class));

        // When/Then: Perform DELETE request
        mockMvc.perform(delete(API_V1_TASKS + "/{id}", 1L))
                .andExpect(status().isNoContent()); // Expect 204

        // Verify calls
        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).delete(task1);
    }

    @Test
    void shouldReturn404OnDeleteIfTaskNotFound() throws Exception {
        // 1. Mock findById (The task is not found)
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then: Perform DELETE request
        mockMvc.perform(delete(API_V1_TASKS + "/{id}", 999L))
                .andExpect(status().isNotFound());

        // Verify only findById was called
        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).delete(any(Task.class));
    }
}
