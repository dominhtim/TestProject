# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
mvn test                  # unit tests only (*Test.java, Surefire) - fast
mvn verify                # unit + integration tests (*IT.java, Failsafe) + JaCoCo reports
mvn clean verify          # what CI runs
mvn spring-boot:run       # run the app on :8080
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # ...with the H2 console
docker compose up --build # app + Prometheus/Jaeger/Loki/Alloy/Grafana
```

Single test / single method:

```bash
mvn test -Dtest=TaskServiceTest
mvn test -Dtest=TaskServiceTest#updateRejectsStaleVersion
mvn verify -Dit.test=TaskConcurrencyIT      # integration tests use -Dit.test, not -Dtest
```

Coverage lands in `target/site/jacoco/` (unit) and `target/site/jacoco-it/` (integration).
Both paths feed Sonar; omitting the `-it` report would understate coverage badly, because
much of this app's behaviour is only exercised over real HTTP.

Local Sonar scan: `mvn verify sonar:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=<org> -Dsonar.projectKey=<key> -Dsonar.token=<token>`.

## Architecture

Spring Boot 4.1 / Java 25 / H2 in-memory. A single Task CRUD resource at `/api/v1/tasks`.

Packages are organised **by feature, not by layer**: `com.example.task` holds the entity,
DTO, repository, API interface, controller and service together; `com.example.shared` holds
only what a second feature would otherwise copy. A new resource is a new package, not an
edit to six layer packages.

The request path is `TaskApi` (mappings + OpenAPI) → `TaskController` (HTTP only) →
`TaskService` (transactions + write rules) → `TaskRepository`. Errors leave through
`GlobalExceptionHandler` as RFC 9457 problem+json.

### The two boundaries that matter

**`TaskService` returns `TaskDto`, never `Task`.** This is structural, not stylistic. A
transaction ends when the service method returns, so an entity handed to a caller is
detached. Mapping outside the boundary works today only because `Task` is all basic
columns — the first lazy association turns it into `LazyInitializationException`, and
`spring.jpa.open-in-view=false` leaves no session to fall back on. `TaskController` never
touches `Task`.

**`GlobalExceptionHandler` does not grow with the API surface.** Every handler is
resource-agnostic; `ResourceNotFoundException` carries its resource type as *data* rather
than as a subclass, so a tenth resource adds no handler and no exception class. Per-controller
`@ExceptionHandler` methods are the thing that would not scale, since each repeats the same
400/409/500 mapping. Detail strings are always fixed text — exception messages can carry SQL
fragments, paths or user data, so they go to the log instead. Note the handler does *not*
cover anything failing before handler dispatch (e.g. a servlet Filter); those hit Boot's
`BasicErrorController` and are governed by `spring.web.error.*`.

### Concurrency / optimistic locking

`Task.version` (`@Version`) turns a silent lost update into `OptimisticLockingFailureException`
→ HTTP 409. Two distinct races land on the same handler: Hibernate's (version moved between
read and flush) and `TaskService`'s (caller sent an already-stale version).

- A `version` on a PUT is a **precondition**. Omitting it *waives the precondition* but does
  **not** opt out of optimistic locking — the version column still guards the flush, so two
  genuinely concurrent writers can both omit it and the loser still gets a 409.
- `update()` uses `saveAndFlush`, not `save`. Hibernate increments the version at flush, which
  for a plain `save` is at commit — after the method has already mapped the entity, so the
  response would carry the pre-increment version and tell the caller its write hadn't happened.
  Flushing also moves the lock failure inside the method rather than into transaction commit.
- `create()` always inserts. Request `id`/`version` are ignored rather than rejected; a fresh
  entity is built from the writable fields, so there is no path by which it can merge into an
  existing row.

### Entity identity

`Task` deliberately does **not** use `@Data` and has no hand-written `equals`/`hashCode`. `@Data`
derives both from every field including the mutable id, so an entity added to a `HashSet` while
transient changes its hash the moment `persist()` assigns an id, and the set can no longer find
it. Object's inherited identity semantics have no such problem, and inside a persistence context
Hibernate already guarantees one instance per row. `TaskEqualityTest` and `TaskPersistenceIT`
exist to catch someone reintroducing field-based equality.

`TaskDto` **does** use `@Data` — it is a detached value object with no persistence identity, so
value equality is correct there. Keeping it separate from the entity also satisfies SonarQube
java:S4684 (entities shouldn't be `@RequestMapping` arguments or return types).

### Sort screening

`TaskController.SORTABLE_PROPERTIES` is an allowlist (`id`, `title`, `completed`). Unscreened,
an unknown `?sort=` reaches Spring Data's criteria builder and blows up as an unhandled runtime
exception → 500 for what is plainly a bad request. The allowlist also stops a caller ordering by
a column the API doesn't expose, which would let them infer its values from the sequence.
`version` is omitted deliberately: it is a concurrency token, not a meaningful ordering.

The set is package-private so `SortablePropertiesTest` can reflectively check every name against
`Task`'s fields — they're string literals the compiler cannot otherwise tie to the entity.

## Spring Boot 4 / Jackson 3 traps

These cost real debugging time. Do not "simplify" them back.

- **`@AllArgsConstructor(onConstructor_ = @__(@JsonCreator(mode = DISABLED)))`** on both `Task`
  and `TaskDto`. Without it, Jackson 3 auto-detects the all-args constructor as the
  deserialization creator (the project compiles with `-parameters`) and then requires *every*
  argument — including primitive `completed` — to be present, breaking partial bodies like
  `{"title": "..."}`. Disabling it keeps Jackson on no-arg constructor + setters.
  `TaskApiIT` has a regression test for this.
- **Two Jackson lines are on the classpath on purpose.** Boot 4 auto-configures Jackson 3
  (`tools.jackson`, via `spring-boot-starter-jackson`). springdoc still generates its OpenAPI
  spec with Jackson 2 internally (upstream: springdoc/springdoc-openapi#3268), which is why
  `com.fasterxml.jackson.core:jackson-databind` is on the **main** classpath, not just test.
- **Never pin `jackson-databind` alone.** It needs a matching `jackson-core` and still borrows
  `jackson-annotations` from the 2.x line; pinning it by itself left annotations mismatched and
  broke serialization at runtime (`NoClassDefFoundError: JsonApplyView`). The `jackson-bom`
  import keeps the Jackson 3 stack consistent.
- `spring-boot-starter-web` → **`spring-boot-starter-webmvc`** in Boot 4.
- Test starters are per-production-starter in Boot 4 (`spring-boot-starter-webmvc-test`,
  `-restclient-test`, `-jackson-test`), each excluding `junit-vintage-engine`.
- `spring-boot-starter-opentelemetry` had no final 4.1.0 release, so its constituent modules are
  declared directly.
- H2 console autoconfiguration is its own module (`spring-boot-h2console`); there is no
  `spring-boot-starter-h2-console` despite some pre-release docs. Without the module,
  `spring.h2.console.enabled` has no effect.
- **`ErrorProperties` moved** from `server.error.*` to `spring.web.error.*`. The old keys are
  silently ignored.
- `@ResponseStatus` on an interface method is **silently ignored** — Spring reads it from the
  implementation method or the controller class only. That's why `TaskApi.deleteTask` returns
  `ResponseEntity` for its 204 instead. Every method returns `ResponseEntity` for uniformity.
- `MySpringAppApplication.main` is package-private (`static void main`), which Boot 4 supports.

## Testing

`*Test.java` = Surefire, `mvn test`. `*IT.java` = Failsafe, `mvn verify` (after packaging, so
`mvn test` stays fast).

| Suite | What only it can show |
| --- | --- |
| `TaskControllerUnitTest` | HTTP surface with the service mocked. `@ControllerAdvice` loads in this slice, so the exception handler is exercised here too. |
| `TaskServiceTest` | The write rules with the repo mocked — at HTTP level the service is a mock, so a controller test can only assert a 409 renders, never that the rule deciding it is right. |
| `GlobalExceptionHandlerTest` | Handler branches unreachable over HTTP: a `FieldError` with no default message, two violations on one field, `DataIntegrityViolationException`. |
| `SortablePropertiesTest` | Allowlist ↔ entity field names, as a build failure instead of a runtime one. |
| `TaskEqualityTest` | Regression guard against field-based equality returning. |
| `TaskDtoTest` | Validation boundaries exhaustively, via `Validator` directly. |
| `TaskApiIT` | Full CRUD over real HTTP on a random port via `RestTestClient` (Boot 4's `TestRestTemplate` replacement). |
| `TaskPersistenceIT` | Real Hibernate: version increments, stale-write rejection, schema constraints. |
| `TaskConcurrencyIT` | The lost-update defence under genuine parallelism. |

Testing gotchas worth knowing before editing these:

- `TaskPersistenceIT` is `@SpringBootTest` (not `@DataJpaTest` — the project doesn't declare
  `spring-boot-starter-data-jpa-test`) and deliberately **not** `@Transactional`: one
  test-managed transaction would share a persistence context across every operation, which is
  exactly what hides version increments and flush-time failures.
- `TaskConcurrencyIT` uses the JDK `HttpClient`, not the injected `RestTestClient`, because the
  requests are genuinely parallel and `HttpClient` is documented thread-safe. It reads ids from
  the `Location` header rather than scraping `"id":` out of the body, which would pick up the
  first nested object that happens to have one.
- With every writer supplying `version=0`, the winner count is **deterministic** (one). With the
  version omitted, it is **not** — a writer whose transaction starts after another commits reads
  the newer version and legitimately succeeds, and full serialisation would let all eight
  succeed. Asserting a fixed winner count there is wrong, and asserting "at least one conflict"
  would be flaky. The invariant that holds regardless of timing is that every success is
  accounted for by exactly one version increment.
- `TaskApiIT` reads POST/PUT bodies as `Map<String, Object>`, not `TaskDto`: `id` is
  `@JsonProperty(READ_ONLY)`, so Jackson drops it when deserializing a response back into the
  DTO — including in the test's own client.
- Unit tests build their `ObjectMapper` directly rather than autowiring: Boot 4's mapper bean is
  Jackson 3's `JsonMapper`, so no bean of Jackson 2's type is guaranteed to exist.

## Build / tooling

- `<release>`, not `<source>`/`<target>` — the latter still compile against the *running* JDK's
  APIs, which previously let a JDK 26 build target a JRE 25 runtime. Keep the Dockerfile's build
  and runtime stages on matching versions regardless.
- `<parameters>true</parameters>` — springdoc needs parameter names in bytecode.
- `lombok.config` sets `addLombokGeneratedAnnotation` so JaCoCo skips generated methods. Without
  it, ~30 generated methods across `Task`/`TaskDto` (several unreachable, e.g. `TaskDto.setId`)
  count as untested production code and bury the genuine gaps.
- `MySpringAppApplication` is excluded from JaCoCo *and* `sonar.coverage.exclusions` — it's one
  `SpringApplication.run()` call and every `@SpringBootTest` already proves the wiring.
- The sonar plugin is unbound; CI invokes `sonar:sonar` explicitly after `verify`.

### CI (`.github/workflows/ci.yml`)

- `code-quality` is skipped for fork PRs **and** for Dependabot PRs. Dependabot runs against
  GitHub's separate Dependabot secret store, so repo Actions secrets resolve empty and the
  scanner rejects the blank projectKey; its branches live in this repo so the fork check doesn't
  catch them. **Do not "fix" this by copying `SONAR_TOKEN` into the Dependabot secret store** —
  that exposes it to arbitrary build-plugin code from an unreviewed dependency bump. Nothing is
  lost: `build-and-test` still runs on every PR and the scan runs on the merge to `main`.
- Sonar secrets go through `env:`, not expanded into `run:` — Actions substitutes
  `${{ secrets.* }}` into raw command text before the shell sees it.
- `fetch-depth: 0` for the Sonar job: "New Code" analysis diffs against history, and a shallow
  checkout makes every line look new.
- The dependency-submission step runs only on `main` (keeps Dependabot alerts accurate).
- `docker-build` is validation only, `push: false`. Nothing is published to a registry.

## Security posture

Read `SECURITY.md` before touching anything here; it is current and specific.

- **There is no Spring Security on the classpath.** Anything exposed over HTTP is reachable by
  anyone who can reach the port.
- The H2 console is **off by default** and lives in the `dev` profile
  (`application-dev.properties`), which `docker-compose.yml` sets. It is a *generic JDBC client*
  — the login form takes an arbitrary url/user/password, so it is not scoped to `taskdb`, and
  reaching it is a code-execution-class exposure. `web-allow-others=true` disables H2's own
  local-only check (needed because Docker's NAT makes requests look non-local); it is a
  workaround, not a mitigation.
- Actuator exposure is a deliberate short allowlist (`health,info,prometheus,metrics`), never
  `*`, with `show-details=never`. Verified: `/actuator/env` and `/actuator/heapdump` 404.
- `spring-boot-health` is declared explicitly as defence-in-depth per the CVE-2026-40976
  advisory (actuator filter-chain bypass), even though 4.1.x is patched.
- `spring.data.web.pageable.max-page-size=100` — without it, `?size=1000000` is an
  unauthenticated way to make the server materialise the whole table.
- `spring.jpa.show-sql=false` — it writes unstructured text to stdout, breaking the ECS JSON log
  format, and can echo row data. Use `logging.level.org.hibernate.SQL` when you need queries.
- **When adding Spring Security**, add an `AccessDeniedException` handler to
  `GlobalExceptionHandler` at the same time. It carries no status and implements no marker
  interface, so it falls into the catch-all and reports **500 instead of 403**, with an ERROR
  stack trace for a routine authorization outcome. `AuthenticationException` needs the same for
  401. The handler can't be written before the class is on the classpath.

## Known false positives — do not "fix"

- **SonarQube java:S2638** on the three `ResponseEntityExceptionHandler` overrides in
  `GlobalExceptionHandler`. The rule claims they change the supertype's nullability contract;
  they don't — the supertype declares each as `protected @Nullable ResponseEntity<Object>` with
  an identical parameter list, importing the same `org.jspecify.annotations.Nullable`. It was
  reported identically with the annotation absent, present, and with the package `@NullMarked`,
  so it isn't responding to anything in the file. Likely the analyzer can't resolve Spring's
  package-level `@NullMarked` from inside the jar. Suppressed at each override; re-check on a
  Sonar upgrade.
- `package-info.java` in `shared.error` is `@NullMarked` because declaring the package's null
  contract is correct on its own merits — **not** because it silenced S2638. It didn't.
- `ResourceNotFoundException.resourceId` is typed `Serializable`, not `Object`, to satisfy
  java:S1948 honestly. Marking it `transient` would silence the rule by making
  `getResourceId()` return null after any round trip.

## Observability

`docker compose up` brings up Prometheus (:9090), Jaeger (:16686), Loki, Alloy (:12345) and
Grafana (:3000, admin/admin). Traces go to Jaeger over OTLP; the compose file overrides
`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` to `http://jaeger:4318` because
`application.properties` defaults to `localhost` for running the app outside Docker.
`observability/prometheus.yml` scrapes `app:8080` by compose service name — change it to
`host.docker.internal:8080` if you run the app on the host instead.

Logs are ECS-structured (`logging.structured.format.console=ecs`), which includes
`trace_id`/`span_id` once tracing is active. Alloy ships container stdout to Loki; Loki indexes
labels only, so JSON fields stay queryable via LogQL's `| json`.

Sampling is `1.0` locally — turn it down in production.
