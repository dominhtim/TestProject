package com.example.task;

import com.example.shared.error.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP surface with TaskService mocked. @ControllerAdvice loads in this slice, so
 *  GlobalExceptionHandler is exercised here too. See CLAUDE.md. */
@WebMvcTest(TaskController.class)
class TaskControllerUnitTest {

    private static final String API_V1_TASKS = "/api/v1/tasks";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    // Built directly: Boot 4's mapper bean is Jackson 3's, so no bean of this type.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TaskService taskService;

    private final TaskDto dto1 =
            TaskDto.builder().id(1L).title("Unit Test Task 1").completed(false).version(0L).build();
    private final TaskDto dto2 =
            TaskDto.builder().id(2L).title("Unit Test Task 2").completed(true).version(3L).build();

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static ResourceNotFoundException taskNotFound(long id) {
        return new ResourceNotFoundException("Task", id);
    }

    @Nested
    @DisplayName("POST /api/v1/tasks")
    class CreateTask {

        @Test
        void shouldReturn201WithLocationHeaderAndBody() throws Exception {
            when(taskService.create(any(TaskDto.class))).thenReturn(dto1);

            TaskDto request = TaskDto.builder().title("Unit Test Task 1").completed(false).build();

            mockMvc.perform(post(API_V1_TASKS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/api/v1/tasks/1")))
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.title", is("Unit Test Task 1")))
                    .andExpect(jsonPath("$.version", is(0)));

            verify(taskService, times(1)).create(any(TaskDto.class));
        }

        @Test
        void shouldRejectBlankTitleWithProblemDetail() throws Exception {
            TaskDto invalidRequest = TaskDto.builder().title(" ").completed(false).build();

            mockMvc.perform(post(API_V1_TASKS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("Validation failed")))
                    .andExpect(jsonPath("$.errors.title", is("Title is mandatory")));

            verify(taskService, never()).create(any(TaskDto.class));
        }

        @Test
        void shouldRejectMissingTitleWithProblemDetail() throws Exception {
            mockMvc.perform(post(API_V1_TASKS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"completed\":true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title", is("Title is mandatory")));

            verify(taskService, never()).create(any(TaskDto.class));
        }

        @Test
        void shouldRejectOverlongTitle() throws Exception {
            TaskDto tooLong = TaskDto.builder().title("x".repeat(Task.TITLE_MAX_LENGTH + 1)).build();

            mockMvc.perform(post(API_V1_TASKS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(tooLong)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title", containsString("at most 255")));

            verify(taskService, never()).create(any(TaskDto.class));
        }

        @Test
        void shouldRejectMalformedJsonWithoutLeakingParserDetail() throws Exception {
            mockMvc.perform(post(API_V1_TASKS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ this is not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Malformed request body")))
                    .andExpect(jsonPath("$.detail", is("The request body is missing or is not valid JSON.")));

            verify(taskService, never()).create(any(TaskDto.class));
        }

        @Test
        void shouldNotBindAClientSuppliedId() throws Exception {
            when(taskService.create(any(TaskDto.class))).thenReturn(dto1);

            // id is READ_ONLY, so it never reaches the service. TaskServiceTest too.
            mockMvc.perform(post(API_V1_TASKS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":9999,\"title\":\"Unit Test Task 1\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tasks")
    class ListTasks {

        @Test
        void shouldReturnPagedEnvelope() throws Exception {
            when(taskService.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(dto1, dto2), PageRequest.of(0, 20), 2));

            mockMvc.perform(get(API_V1_TASKS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].title", is(dto1.getTitle())))
                    .andExpect(jsonPath("$.page.totalElements", is(2)))
                    .andExpect(jsonPath("$.page.number", is(0)));

            verify(taskService, times(1)).findAll(any(Pageable.class));
        }

        @Test
        void shouldReturnEmptyContentArrayWhenNoTasksExist() throws Exception {
            when(taskService.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get(API_V1_TASKS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.totalElements", is(0)));
        }

        @Test
        void shouldAcceptAnAllowlistedSortProperty() throws Exception {
            when(taskService.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(dto1), PageRequest.of(0, 20), 1));

            mockMvc.perform(get(API_V1_TASKS + "?sort=title,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));

            verify(taskService, times(1)).findAll(any(Pageable.class));
        }

        @Test
        void shouldRejectAnUnknownSortPropertyAsBadRequestNotServerError() throws Exception {
            // Unscreened this fails in the criteria builder and reports as a 500.
            mockMvc.perform(get(API_V1_TASKS + "?sort=nonexistentField"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("Invalid sort property")))
                    .andExpect(jsonPath("$.property", is("nonexistentField")))
                    .andExpect(jsonPath("$.sortableProperties", hasSize(3)));

            verify(taskService, never()).findAll(any(Pageable.class));
        }

        @Test
        void shouldRejectAnUnknownSortPropertyEvenAlongsideAValidOne() throws Exception {
            mockMvc.perform(get(API_V1_TASKS + "?sort=title&sort=secretColumn"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.property", is("secretColumn")));

            verify(taskService, never()).findAll(any(Pageable.class));
        }

        @Test
        void shouldRejectSortingByVersionWhichIsDeliberatelyNotExposed() throws Exception {
            mockMvc.perform(get(API_V1_TASKS + "?sort=version"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.property", is("version")));

            verify(taskService, never()).findAll(any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tasks/{id}")
    class GetTaskById {

        @Test
        void shouldReturnTask() throws Exception {
            when(taskService.findById(1L)).thenReturn(dto1);

            mockMvc.perform(get(API_V1_TASKS + "/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.title", is(dto1.getTitle())));

            verify(taskService, times(1)).findById(1L);
        }

        @Test
        void shouldReturn404ProblemDetailWhenAbsent() throws Exception {
            when(taskService.findById(999L)).thenThrow(taskNotFound(999L));

            mockMvc.perform(get(API_V1_TASKS + "/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("Task not found")))
                    .andExpect(jsonPath("$.resourceType", is("Task")))
                    .andExpect(jsonPath("$.resourceId", is(999)));
        }

        @Test
        void shouldReturn400ForNonNumericIdWithoutLeakingTypeNames() throws Exception {
            mockMvc.perform(get(API_V1_TASKS + "/{id}", "not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid parameter")))
                    .andExpect(jsonPath("$.detail", is("A request parameter has the wrong type.")));

            verify(taskService, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/tasks/{id}")
    class UpdateTask {

        @Test
        void shouldUpdateAndReturnTheNewVersion() throws Exception {
            TaskDto updateRequest = TaskDto.builder().title("Updated Title").completed(true).build();
            TaskDto updated =
                    TaskDto.builder().id(1L).title("Updated Title").completed(true).version(1L).build();

            when(taskService.update(eq(1L), any(TaskDto.class))).thenReturn(updated);

            mockMvc.perform(put(API_V1_TASKS + "/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is("Updated Title")))
                    .andExpect(jsonPath("$.completed", is(true)))
                    .andExpect(jsonPath("$.version", is(1)));
        }

        @Test
        void shouldReturn409WhenTheServiceReportsAStaleVersion() throws Exception {
            TaskDto staleRequest = TaskDto.builder().title("Stale writer").completed(true).version(1L).build();

            when(taskService.update(eq(2L), any(TaskDto.class)))
                    .thenThrow(new OptimisticLockingFailureException("version 1 is stale"));

            mockMvc.perform(put(API_V1_TASKS + "/{id}", 2L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(staleRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("Concurrent modification")));
        }

        @Test
        void shouldReturn404WhenTaskAbsent() throws Exception {
            TaskDto updateRequest = TaskDto.builder().title("Non-existent").completed(true).build();

            when(taskService.update(eq(999L), any(TaskDto.class))).thenThrow(taskNotFound(999L));

            mockMvc.perform(put(API_V1_TASKS + "/{id}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.resourceId", is(999)));
        }

        @Test
        void shouldRejectBlankTitleBeforeReachingTheService() throws Exception {
            TaskDto invalidRequest = TaskDto.builder().title("   ").completed(true).build();

            mockMvc.perform(put(API_V1_TASKS + "/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title", is("Title is mandatory")));

            verify(taskService, never()).update(any(), any(TaskDto.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/tasks/{id}")
    class DeleteTask {

        @Test
        void shouldReturn204AndDelete() throws Exception {
            doNothing().when(taskService).delete(1L);

            mockMvc.perform(delete(API_V1_TASKS + "/{id}", 1L))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(taskService, times(1)).delete(1L);
        }

        @Test
        void shouldReturn404WhenTaskAbsent() throws Exception {
            doThrow(taskNotFound(999L)).when(taskService).delete(999L);

            mockMvc.perform(delete(API_V1_TASKS + "/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Task not found")));
        }

        @Test
        void shouldReturn409WhenTheVersionedDeleteLosesToAConcurrentWrite() throws Exception {
            doThrow(new OptimisticLockingFailureException("row changed")).when(taskService).delete(1L);

            mockMvc.perform(delete(API_V1_TASKS + "/{id}", 1L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title", is("Concurrent modification")));
        }
    }

    @Nested
    @DisplayName("Unexpected failures")
    class UnexpectedFailures {

        @Test
        void shouldReturn500ProblemDetailWithoutLeakingTheExceptionMessage() throws Exception {
            when(taskService.findById(1L))
                    .thenThrow(new IllegalStateException("connection string: jdbc://secret-host/db"));

            mockMvc.perform(get(API_V1_TASKS + "/{id}", 1L))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.title", is("Internal server error")))
                    .andExpect(jsonPath("$.detail", is("An unexpected error occurred.")));
        }
    }
}
