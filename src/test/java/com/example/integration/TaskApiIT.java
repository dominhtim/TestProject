package com.example.integration;

import com.example.dto.TaskDto;
import com.example.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full app on a random port, exercised over real HTTP via
 * {@link RestTestClient} (Boot 4's replacement for {@code TestRestTemplate}).
 * The {@code IT} suffix puts it in failsafe's {@code mvn verify} run rather
 * than the fast {@code mvn test} one.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class TaskApiIT {

    private static final String API_V1_TASKS = "/api/v1/tasks";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

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
        // A Map, not TaskDto: id is READ_ONLY on the DTO, so Jackson won't
        // deserialize it back out even though it's there on the wire.
        TaskDto newTask = TaskDto.builder().title("Ship the release").completed(false).build();
        var createResult = restClient.post().uri(API_V1_TASKS)
                .body(newTask)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JSON_OBJECT)
                .returnResult();

        Map<String, Object> created = createResult.getResponseBody();
        assertThat(created).isNotNull();
        Long id = ((Number) created.get("id")).longValue();
        assertThat(created).containsEntry("title", "Ship the release");
        assertThat(created).containsEntry("version", 0);

        URI location = createResult.getResponseHeaders().getLocation();
        assertThat(location)
                .as("201 Created must point at the new resource")
                .isNotNull();
        assertThat(location.getPath()).endsWith(API_V1_TASKS + "/" + id);

        // The Location header is not decorative - it has to resolve.
        restClient.get().uri(location.getPath())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Ship the release");

        restClient.get().uri(API_V1_TASKS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.page.totalElements").isEqualTo(1);

        TaskDto updateDetails = TaskDto.builder().title("Ship the release (done)").completed(true).build();
        Map<String, Object> updated = restClient.put().uri(API_V1_TASKS + "/{id}", id)
                .body(updateDetails)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JSON_OBJECT)
                .returnResult()
                .getResponseBody();

        assertThat(updated).isNotNull()
                .containsEntry("completed", true)
                .as("the optimistic lock counter must advance on write")
                .containsEntry("version", 1);

        restClient.delete().uri(API_V1_TASKS + "/{id}", id)
                .exchange()
                .expectStatus().isNoContent();

        restClient.get().uri(API_V1_TASKS + "/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldPaginateRatherThanReturningEveryRow() {
        for (int index = 0; index < 25; index++) {
            restClient.post().uri(API_V1_TASKS)
                    .body(TaskDto.builder().title("Task " + index).build())
                    .exchange()
                    .expectStatus().isCreated();
        }

        // Default page size is 20, so 25 rows must not all come back at once.
        restClient.get().uri(API_V1_TASKS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(20)
                .jsonPath("$.page.totalElements").isEqualTo(25)
                .jsonPath("$.page.totalPages").isEqualTo(2);

        restClient.get().uri(API_V1_TASKS + "?page=1&size=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(5)
                .jsonPath("$.page.number").isEqualTo(1);
    }

    @Test
    void shouldCapAnOversizedPageSizeRequest() {
        // Without max-page-size this is an unauthenticated way to ask the
        // server to materialise the whole table.
        restClient.get().uri(API_V1_TASKS + "?size=100000")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.size").isEqualTo(100);
    }

    @Test
    void shouldRejectInvalidTaskWithProblemDetail() {
        TaskDto blankTitleTask = TaskDto.builder().title("  ").completed(false).build();

        restClient.post().uri(API_V1_TASKS)
                .body(blankTitleTask)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation failed")
                .jsonPath("$.errors.title").isEqualTo("Title is mandatory");
    }

    @Test
    void shouldReturn404ProblemDetailForUnknownTask() {
        restClient.get().uri(API_V1_TASKS + "/{id}", 999_999L)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Task not found")
                .jsonPath("$.resourceType").isEqualTo("Task")
                .jsonPath("$.resourceId").isEqualTo(999_999);
    }

    @Test
    void shouldReturn400WithoutLeakingInternalsForANonNumericId() {
        restClient.get().uri(API_V1_TASKS + "/{id}", "not-a-number")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("A request parameter has the wrong type.");
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
                .expectStatus().isCreated()
                .expectBody(TaskDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Buy groceries");
        assertThat(created.isCompleted()).isFalse();
        assertThat(created.getVersion()).isZero();
    }
}
