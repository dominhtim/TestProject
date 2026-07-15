package com.example.integration;

import com.example.model.Task;
import com.example.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True end-to-end integration test for the Task API.
 *
 * Unlike {@code com.example.controller.TaskControllerUnitTest} (mocked
 * repository, no Spring context) this test boots the full Spring Boot
 * application on a random port and exercises it over real HTTP using
 * {@link RestTestClient} — the Spring Boot 4 / Spring Framework 7
 * replacement for {@code TestRestTemplate} — verifying the entire stack:
 * embedded servlet container, JSON serialization, bean validation, and the
 * real H2-backed repository.
 *
 * Lives under {@code com.example.integration} (separate from the
 * controller-level unit tests) and is named with the {@code IT} suffix so
 * maven-failsafe-plugin picks it up during {@code mvn verify}, keeping it
 * out of the fast {@code mvn test} unit-test run.
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
        // CREATE
        Task newTask = new Task(null, "Ship the release", false);
        Task created = restClient.post().uri(API_V1_TASKS)
                .body(newTask)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Task.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        Long id = created.getId();
        assertThat(id).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Ship the release");

        // READ ALL
        restClient.get().uri(API_V1_TASKS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);

        // READ ONE
        restClient.get().uri(API_V1_TASKS + "/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Ship the release");

        // UPDATE
        Task updateDetails = new Task(id, "Ship the release (done)", true);
        Task updated = restClient.put().uri(API_V1_TASKS + "/{id}", id)
                .body(updateDetails)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Task.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.isCompleted()).isTrue();

        // DELETE
        restClient.delete().uri(API_V1_TASKS + "/{id}", id)
                .exchange()
                .expectStatus().isNoContent();

        restClient.get().uri(API_V1_TASKS + "/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldRejectInvalidTaskWithBadRequest() {
        Task blankTitleTask = new Task(null, "  ", false);

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
}
