package com.example.controller;

import com.example.dto.TaskDto;
import com.example.model.Task;
import com.example.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
 *
 * The controller's wire format is {@link TaskDto} (see TaskController/TaskDto
 * javadoc), while the mocked TaskRepository still deals in the {@link Task}
 * entity - these tests build both: TaskDto for request bodies, Task entities
 * for repository mock behavior.
 */
@WebMvcTest(TaskController.class)
class TaskControllerUnitTest {

    private static final String API_V1_TASKS = "/api/v1/tasks";

    @Autowired
    private MockMvc mockMvc;

    // Built directly rather than autowired: Spring Boot 4's auto-configured
    // JSON mapper bean is Jackson 3's JsonMapper, not this (Jackson 2)
    // ObjectMapper type, so there's no guarantee a matching bean exists in
    // the context. This is just a local test utility for building request
    // JSON, independent of whatever the app itself uses at runtime.
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mock the TaskRepository dependency
    @MockitoBean
    private TaskRepository taskRepository;

    // Entities returned by the mocked repository
    private final Task task1 = new Task(1L, "Unit Test Task 1", false);
    private final Task task2 = new Task(2L, "Unit Test Task 2", true);

    @Test
    void shouldCreateTask() throws Exception {
        // Given: Mock the save operation to return the persisted entity (with an ID)
        when(taskRepository.save(any(Task.class))).thenReturn(task1);

        TaskDto request = new TaskDto(null, "Unit Test Task 1", false);

        // When/Then: Perform POST request
        mockMvc.perform(post(API_V1_TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Unit Test Task 1")));

        // Verify the repository save method was called exactly once
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void shouldReturnBadRequestOnCreateIfTitleIsMissing() throws Exception {
        // Request with missing (blank) title should fail validation
        TaskDto invalidRequest = new TaskDto(null, " ", false);

        // When/Then: Perform POST request
        mockMvc.perform(post(API_V1_TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // Verify that the repository save method was never called
        verify(taskRepository, never()).save(any(Task.class));
    }


    @Test
    void shouldGetAllTasks() throws Exception {
        // Given: Mock findAll to return a list of entities
        when(taskRepository.findAll()).thenReturn(Arrays.asList(task1, task2));

        // When/Then: Perform GET request
        mockMvc.perform(get(API_V1_TASKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is(task1.getTitle())));

        // Verify findAll was called
        verify(taskRepository, times(1)).findAll();
    }

    @Test
    void shouldGetTaskById() throws Exception {
        // Given: Mock findById to return the entity
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

    @Test
    void shouldUpdateTask() throws Exception {
        // Request body for the update
        TaskDto updateRequest = new TaskDto(1L, "Updated Title", true);
        // Entity the repository returns after save
        Task updatedEntity = new Task(1L, "Updated Title", true);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));
        when(taskRepository.save(any(Task.class))).thenReturn(updatedEntity);

        // When/Then: Perform PUT request
        mockMvc.perform(put(API_V1_TASKS + "/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.completed", is(true)));

        // Verify calls
        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void shouldReturn404OnUpdateIfTaskNotFound() throws Exception {
        // Request body for the update
        TaskDto updateRequest = new TaskDto(999L, "Non-existent", true);

        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then: Perform PUT request
        mockMvc.perform(put(API_V1_TASKS + "/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        // Verify only findById was called
        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void shouldDeleteTask() throws Exception {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));
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
