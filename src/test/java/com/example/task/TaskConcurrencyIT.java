package com.example.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The lost-update defence end to end. Uses the JDK HttpClient, not RestTestClient,
 *  because these requests are genuinely parallel. See CLAUDE.md. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TaskConcurrencyIT {

    private static final int CONCURRENT_WRITERS = 8;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private TaskRepository taskRepository;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @BeforeEach
    void clearDatabase() {
        taskRepository.deleteAll();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> createTask(String title) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/tasks"))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"" + title + "\"}"))
                .build();
        return send(request);
    }

    private HttpRequest updateRequest(long id, String title, Long expectedVersion) {
        String versionMember = expectedVersion == null ? "" : ",\"version\":" + expectedVersion;
        String body = "{\"title\":\"" + title + "\",\"completed\":true" + versionMember + "}";
        return HttpRequest.newBuilder(uri("/api/v1/tasks/" + id))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static List<Integer> runConcurrently(List<Callable<Integer>> writers) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(writers.size());
        try {
            for (Future<Integer> result : executor.invokeAll(writers, TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                statuses.add(result.get());
            }
        } finally {
            executor.shutdownNow();
        }
        return statuses;
    }

    /** From the Location header, not scraped from the body, which would pick up the
     *  first nested object that happens to have an id. */
    private static long idOf(HttpResponse<String> created) {
        String location = created.headers().firstValue("Location")
                .orElseThrow(() -> new AssertionError("a 201 response must carry a Location header"));
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private List<Callable<Integer>> writersAll(long id, Long suppliedVersion) {
        CyclicBarrier startTogether = new CyclicBarrier(CONCURRENT_WRITERS);
        List<Callable<Integer>> writers = new ArrayList<>();
        for (int writer = 0; writer < CONCURRENT_WRITERS; writer++) {
            int writerNumber = writer;
            writers.add(() -> {
                startTogether.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                return send(updateRequest(id, "Written by writer " + writerNumber, suppliedVersion)).statusCode();
            });
        }
        return writers;
    }

    @Test
    void exactlyOneOfManySimultaneousWritersWins() throws Exception {
        HttpResponse<String> created = createTask("Contended task");
        assertThat(created.statusCode()).isEqualTo(201);
        long id = idOf(created);

        // Every writer supplies version 0, which makes the winner count deterministic.
        List<Integer> statuses = runConcurrently(writersAll(id, 0L));

        assertThat(statuses)
                .as("every writer must get a definitive answer, not a 500")
                .allMatch(status -> status == 200 || status == 409);
        assertThat(statuses.stream().filter(status -> status == 200).count())
                .as("exactly one writer may win; the rest must be told they lost")
                .isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count())
                .isEqualTo(CONCURRENT_WRITERS - 1L);

        assertThat(taskRepository.findById(id).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void aSecondWriteAtTheSameVersionIsRejected() throws Exception {
        long id = idOf(createTask("Sequential contention"));

        assertThat(send(updateRequest(id, "First edit", 0L)).statusCode()).isEqualTo(200);

        HttpResponse<String> stale = send(updateRequest(id, "Second edit from a stale read", 0L));

        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("Concurrent modification");
        assertThat(taskRepository.findById(id).orElseThrow().getTitle()).isEqualTo("First edit");
    }

    /** Waiving the precondition accepts a second write only because these do not
     *  overlap. Not an opt-out from optimistic locking. */
    @Test
    void omittingTheVersionWaivesThePreconditionForSequentialWrites() throws Exception {
        long id = idOf(createTask("Unconditional"));

        assertThat(send(updateRequest(id, "First unconditional edit", null)).statusCode()).isEqualTo(200);
        assertThat(send(updateRequest(id, "Second unconditional edit", null)).statusCode()).isEqualTo(200);

        assertThat(taskRepository.findById(id).orElseThrow().getTitle())
                .isEqualTo("Second unconditional edit");
    }

    /** No fixed winner count here, and asserting one is wrong - see CLAUDE.md. What holds
     *  regardless of timing: every success is exactly one version increment. */
    @Test
    void omittingTheVersionStillAccountsForEveryWrite() throws Exception {
        long id = idOf(createTask("Contended, unconditionally"));

        List<Integer> statuses = runConcurrently(writersAll(id, null));
        long winners = statuses.stream().filter(status -> status == 200).count();

        assertThat(statuses)
                .as("every writer must get a definitive answer, not a 500")
                .allMatch(status -> status == 200 || status == 409);
        assertThat(taskRepository.findById(id).orElseThrow().getVersion())
                .as("one version bump per accepted write, none per rejected one")
                .isEqualTo(winners);
    }
}
