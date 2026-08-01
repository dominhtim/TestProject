package com.example.controller;

import com.example.dto.TaskDto;
import com.example.model.Task;
import com.example.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
 * Controller-level unit test: {@code @WebMvcTest} loads only TaskController
 * with TaskRepository mocked, so requests use {@link TaskDto} while the mock
 * deals in the {@link Task} entity, matching TaskController's mapping.
 */
@WebMvcTest(TaskController.class)
class TaskControllerUnitTest {

    private static final String API_V1_TASKS = "/api/v1/tasks";

    @Autowired
    private MockMvc mockMvc;

    // Built directly rather than autowired: Spring Boot 4's autoconfigured
    // JSON mapper bean is Jackson 3's JsonMapper, not this (Jackson 2)
    // ObjectMapper type, so there's no guarantee a matching bean exists in
    // the context.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TaskRepository taskRepository;

    private final Task task1 = Task.builder().id(1L).title("Unit Test Task 1").completed(false).build();
    private final Task task2 = Task.builder().id(2L).title("Unit Test Task 2").completed(true).build();

    @Test
    void shouldCreateTask() throws Exception {
        when(taskRepository.save(any(Task.class))).thenReturn(task1);

        TaskDto request = TaskDto.builder().title("Unit Test Task 1").completed(false).build();

        mockMvc.perform(post(API_V1_TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Unit Test Task 1")));

        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void shouldReturnBadRequestOnCreateIfTitleIsMissing() throws Exception {
        TaskDto invalidRequest = TaskDto.builder().title(" ").completed(false).build();

        mockMvc.perform(post(API_V1_TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void shouldGetAllTasks() throws Exception {
        when(taskRepository.findAll()).thenReturn(Arrays.asList(task1, task2));

        mockMvc.perform(get(API_V1_TASKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is(task1.getTitle())));

        verify(taskRepository, times(1)).findAll();
    }

    @Test
    void shouldGetTaskById() throws Exception {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));

        mockMvc.perform(get(API_V1_TASKS + "/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is(task1.getTitle())));

        verify(taskRepository, times(1)).findById(1L);
    }

    @Test
    void shouldReturn404IfTaskNotFound() throws Exception {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get(API_V1_TASKS + "/{id}", 999L))
                .andExpect(status().isNotFound());

        verify(taskRepository, times(1)).findById(999L);
    }

    @Test
    void shouldUpdateTask() throws Exception {
        TaskDto updateRequest = TaskDto.builder().id(1L).title("Updated Title").completed(true).build();
        Task updatedEntity = Task.builder().id(1L).title("Updated Title").completed(true).build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));
        when(taskRepository.save(any(Task.class))).thenReturn(updatedEntity);

        mockMvc.perform(put(API_V1_TASKS + "/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.completed", is(true)));

        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void shouldReturn404OnUpdateIfTaskNotFound() throws Exception {
        TaskDto updateRequest = TaskDto.builder().id(999L).title("Non-existent").completed(true).build();

        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put(API_V1_TASKS + "/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void shouldDeleteTask() throws Exception {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));
        doNothing().when(taskRepository).delete(any(Task.class));

        mockMvc.perform(delete(API_V1_TASKS + "/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).delete(task1);
    }

    @Test
    void shouldReturn404OnDeleteIfTaskNotFound() throws Exception {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete(API_V1_TASKS + "/{id}", 999L))
                .andExpect(status().isNotFound());

        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).delete(any(Task.class));
    }
}
