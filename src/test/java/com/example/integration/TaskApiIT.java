package com.example.integration;

import com.example.dto.TaskDto;
import com.example.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for the Task API: boots the full Spring Boot app on a
 * random port and exercises it over real HTTP via {@link RestTestClient}
 * (the Spring Boot 4 / Spring Framework 7 replacement for
 * {@code TestRestTemplate}), unlike the mocked-repository
 * {@code TaskControllerUnitTest}.
 * <p>
 * Named with the {@code IT} suffix so maven-failsafe-plugin picks it up
 * during {@code mvn verify}, separate from the fast {@code mvn test} run.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class TaskApiIT {

    private static final String API_V1_TASKS = "/api/v1/tasks";

    @Autowired
    private RestTestClient restClient;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setup() {
        taskRepository.deleteAll();
    }

    @Test
    void shouldSupportFullCrudLifecycleOverRealHttp() {
        TaskDto newTask = TaskDto.builder().title("Ship the release").completed(false).build();
        TaskDto created = restClient.post().uri(API_V1_TASKS)
                .body(newTask)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TaskDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        Long id = created.getId();
        assertThat(id).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Ship the release");

        restClient.get().uri(API_V1_TASKS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);

        restClient.get().uri(API_V1_TASKS + "/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Ship the release");

        TaskDto updateDetails = TaskDto.builder().id(id).title("Ship the release (done)").completed(true).build();
        TaskDto updated = restClient.put().uri(API_V1_TASKS + "/{id}", id)
                .body(updateDetails)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TaskDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.isCompleted()).isTrue();

        restClient.delete().uri(API_V1_TASKS + "/{id}", id)
                .exchange()
                .expectStatus().isNoContent();

        restClient.get().uri(API_V1_TASKS + "/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldRejectInvalidTaskWithBadRequest() {
        TaskDto blankTitleTask = TaskDto.builder().title("  ").completed(false).build();

        restClient.post().uri(API_V1_TASKS)
                .body(blankTitleTask)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn404ForUnknownTask() {
        restClient.get().uri(API_V1_TASKS + "/{id}", 999_999L)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldCreateTaskFromMinimalJsonMissingOptionalFields() {
        // Regression test: Jackson must not require "completed"/"id" just
        // because TaskDto also has an all-args constructor (see TaskDto).
        Map<String, Object> minimalPayload = Map.of("title", "Buy groceries");

        TaskDto created = restClient.post().uri(API_V1_TASKS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(minimalPayload)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TaskDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Buy groceries");
        assertThat(created.isCompleted()).isFalse();
    }
}
