package com.example.integration;

import com.example.model.Task;
import com.example.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True end-to-end integration test for the Task API.
 *
 * Unlike {@code com.example.controller.TaskControllerUnitTest} (mocked
 * repository, no Spring context) this test boots the full Spring Boot
 * application on a random port and exercises it over real HTTP with
 * {@link TestRestTemplate}, verifying the entire stack: embedded servlet
 * container, JSON serialization, bean validation, and the real
 * H2-backed repository.
 *
 * Lives under {@code com.example.integration} (separate from the
 * controller-level unit tests) and is named with the {@code IT} suffix
 * so maven-failsafe-plugin picks it up during {@code mvn verify}, keeping
 * it out of the fast {@code mvn test} unit-test run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskApiIT {

    private static final String API_V1_TASKS = "/api/v1/tasks";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setup() {
        taskRepository.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void shouldSupportFullCrudLifecycleOverRealHttp() {
        // CREATE
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Task newTask = new Task(null, "Ship the release", false);
        HttpEntity<Task> createRequest = new HttpEntity<>(newTask, headers);

        ResponseEntity<Task> createResponse =
                restTemplate.postForEntity(url(API_V1_TASKS), createRequest, Task.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        Long id = createResponse.getBody().getId();
        assertThat(id).isNotNull();
        assertThat(createResponse.getBody().getTitle()).isEqualTo("Ship the release");

        // READ ALL
        ResponseEntity<Task[]> listResponse = restTemplate.getForEntity(url(API_V1_TASKS), Task[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);

        // READ ONE
        ResponseEntity<Task> getResponse = restTemplate.getForEntity(url(API_V1_TASKS + "/" + id), Task.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getTitle()).isEqualTo("Ship the release");

        // UPDATE
        Task updated = new Task(id, "Ship the release (done)", true);
        HttpEntity<Task> updateRequest = new HttpEntity<>(updated, headers);
        ResponseEntity<Task> updateResponse = restTemplate.exchange(
                url(API_V1_TASKS + "/" + id), HttpMethod.PUT, updateRequest, Task.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().isCompleted()).isTrue();

        // DELETE
        restTemplate.delete(url(API_V1_TASKS + "/" + id));

        ResponseEntity<Task> afterDelete = restTemplate.getForEntity(url(API_V1_TASKS + "/" + id), Task.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectInvalidTaskWithBadRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Task blankTitleTask = new Task(null, "  ", false);
        HttpEntity<Task> request = new HttpEntity<>(blankTitleTask, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity(url(API_V1_TASKS), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn404ForUnknownTask() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(url(API_V1_TASKS + "/999999"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
