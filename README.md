# Spring Boot 4.1 CRUD Application with JDK 25

This is a simple **To-Do Task CRUD (Create, Read, Update, Delete)**
application built with **Spring Boot 4.1**, **Java 25 (LTS)**, **Spring Data
JPA**, and the **H2 in-memory database**.

------------------------------------------------------------------------

## Prerequisites

-   **JDK 25** (Set in `pom.xml`)
-   **Maven 3.8+**

------------------------------------------------------------------------

## How to Run

### Clone the Repository

### Run the Application (using Maven)

    mvn spring-boot:run

The application will start on:\
**http://localhost:8080**

------------------------------------------------------------------------

## Project Layout

Packages are organised by feature, not by layer:

```
com/example/
├── task/                    everything about Task, in one place
│   ├── Task, TaskDto, TaskRepository
│   ├── TaskApi, TaskController      HTTP contract + mapping
│   └── TaskService                  transaction boundary + write rules
└── shared/
    ├── config/              OpenApiConfig
    ├── error/               GlobalExceptionHandler + the exceptions it maps
    └── web/                 SortablePropertyValidator, composed @ApiResponse annotations
```

A second resource is one new package under `com/example/`, not an edit to six
layer packages. `shared/` is deliberately small — something belongs there only
once a second feature would otherwise copy it.

Tests mirror the same structure, so `com.example.task` holds the unit tests,
the service tests and the integration tests for that feature together.

------------------------------------------------------------------------

## API Endpoints (CRUD)

All endpoints use the base URL:\
**http://localhost:8080/api/v1/tasks**

| Method | Path | Description | Request Body (JSON) | Response |
| ------ | ---- | ----------- | ------------------- | -------- |
| POST | `/` | Create a new task. | `{ "title": "New Task Title" }` | **201 Created** + `Location` header, or 400 |
| GET | `/` | Retrieve a **page** of tasks. Query params: `page`, `size`, `sort`. | – | 200 OK |
| GET | `/{id}` | Retrieve a single task by ID. | – | 200 OK, 400 (non-numeric ID), or 404 |
| PUT | `/{id}` | Replace an existing task. | `{ "title": "Updated Title", "completed": true, "version": 0 }` | 200 OK, 400, 404, or **409 Conflict** |
| DELETE | `/{id}` | Delete a task by ID. | – | 204 No Content, or 404 |

### Pagination

`GET /api/v1/tasks` is paginated — an unbounded `findAll()` would load the
whole table into heap on every call. The response envelope is Spring Data's
`PagedModel`:

```json
{
  "content": [ { "id": 1, "title": "Buy groceries", "completed": false, "version": 0 } ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

Default page size is 20 and the maximum a client may request is 100
(`spring.data.web.pageable.*`).

`sort` accepts `id`, `title` and `completed` only. Any other property is a
**400**, not a 500 — an unscreened name would otherwise reach the criteria
builder and fail there, and sorting by an unexposed column would leak its
ordering.

### Optimistic locking

Every task carries a `version` counter. Include the version you last read in
a `PUT` and the update is rejected with **409 Conflict** if anyone has
written to that task since — re-read it and retry.

Omitting `version` waives that precondition, so a write based on a read from
ten minutes ago will be accepted. It does **not** opt out of optimistic
locking: Hibernate still checks the version column when it flushes, so two
genuinely concurrent writers can both omit `version` and the one that commits
second still gets a 409. Any client issuing a `PUT` should be prepared to
handle 409 regardless of whether it sends a version.

### Error responses

Every error raised by a matched controller method — including framework-level
ones like a malformed body or a non-numeric ID — is returned as an RFC 9457
`application/problem+json` document produced by `GlobalExceptionHandler`:

```json
{
  "type": "https://api.example.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "errors": { "title": "Title is mandatory" }
}
```

Exception messages are never echoed into the response body; they go to the
log instead, correlated by trace ID.

One gap worth knowing about: `@ControllerAdvice` only runs once a request has
been dispatched to a handler method. Anything failing earlier — an exception
thrown in a servlet `Filter`, for instance — is served by Spring Boot's
`BasicErrorController` and is governed by the `spring.web.error.*` properties,
which are set to `never`. Those responses are deliberately bare.

------------------------------------------------------------------------

## API Documentation (Swagger / OpenAPI)

Interactive API docs are available once the app is running:

-   **Swagger UI:** http://localhost:8080/swagger-ui.html
-   **OpenAPI spec (JSON):** http://localhost:8080/v3/api-docs

> **Known issue:** `springdoc-openapi` 3.0.3 still generates its spec using
> Jackson 2 internally even on Spring Boot 4/Jackson 3
> ([tracked upstream](https://github.com/springdoc/springdoc-openapi/issues/3268)).
> If these endpoints throw a Jackson-related error, that's why - check the
> issue for the current status.

------------------------------------------------------------------------

## Observability

The app is instrumented with Actuator, Micrometer, and OpenTelemetry.

| What                | Where                                       |
| ------------------- | -------------------------------------------- |
| Health check         | http://localhost:8080/actuator/health        |
| App info             | http://localhost:8080/actuator/info          |
| Prometheus metrics   | http://localhost:8080/actuator/prometheus    |
| Structured logs      | Console, JSON (ECS format); shipped to Loki via Alloy in the Docker stack |
| Distributed tracing  | Exported via OTLP to Jaeger                  |

Only `health`, `info`, `prometheus`, and `metrics` are exposed
(`management.endpoints.web.exposure.include` in `application.properties`) -
this app has no Spring Security configured, so anything exposed here is
reachable by anyone. See [`SECURITY.md`](SECURITY.md) for more on this.

### Running the full observability stack locally

```
docker compose up --build
```

This builds the app image and starts it alongside Prometheus, Jaeger,
Loki+Alloy, and Grafana, all wired together:

-   **App:** http://localhost:8080
-   **Prometheus:** http://localhost:9090
-   **Jaeger UI:** http://localhost:16686
-   **Alloy UI:** http://localhost:12345 (log collector debug/status page)
-   **Grafana:** http://localhost:3000 (login `admin` / `admin`, Prometheus
    and Loki datasources are pre-provisioned)

Logs from every container in the stack are collected by Alloy and shipped to
Loki (see [`observability/alloy/config.alloy`](observability/alloy/config.alloy)).
In Grafana's Explore view, pick the Loki datasource and query, for example,
`{container="testproject-app-1"}` to see just the app's logs, or add
`| json | status >= 500` to filter to error responses.

If you'd rather run the app directly (`mvn spring-boot:run`) and only the
observability tools in Docker, start just those services
(`docker compose up prometheus jaeger loki alloy grafana`) and change the
target in [`observability/prometheus.yml`](observability/prometheus.yml) from
`app:8080` to `host.docker.internal:8080`. Note Alloy will then only see logs
from the other Docker containers, not the app running on your host.

------------------------------------------------------------------------

## Concurrency

The app runs each request on a virtual thread
(`spring.threads.virtual.enabled=true`), so existing blocking JPA/JDBC code
scales to a large number of concurrent requests without a reactive rewrite -
a blocked virtual thread doesn't tie up an OS thread the way a blocked
platform thread would. Requires Java 21+ (this project targets 25).

With the platform-thread ceiling removed, the JDBC connection pool becomes
the real concurrency limit, so `spring.datasource.hikari.maximum-pool-size`
is raised from HikariCP's default of 10 to 50.

------------------------------------------------------------------------

## Database Console

The H2 in-memory database console is **off by default** and lives in the
`dev` profile ([`application-dev.properties`](src/main/resources/application-dev.properties)).
`docker compose up` sets that profile, so the local stack has it at:\
**http://localhost:8080/h2-console**

Running the app directly needs the profile passed explicitly:

    mvn spring-boot:run -Dspring-boot.run.profiles=dev

**JDBC URL:** `jdbc:h2:mem:taskdb`\
**Username:** `sa`\
**Password:** `password`

> ⚠️ **The `dev` profile is for local use only.**
> The console is not a viewer for `taskdb` — it is a generic H2 client whose
> login form takes an arbitrary JDBC url, user and password, so reaching it
> means arbitrary SQL against a connection target of the caller's choosing.
> There is no Spring Security on the classpath to gate it, and the `dev`
> profile also sets `spring.h2.console.settings.web-allow-others=true`, which
> turns off H2's own local-only check (the Docker setup makes requests arrive
> via NAT rather than loopback, so the console is refused without it — the
> flag is a workaround, not a hardening measure).
>
> The default profile is the safe one, so a deployment has to opt in rather
> than remember to opt out. Do not set `SPRING_PROFILES_ACTIVE=dev` on any
> network you do not fully control. If you ever need the console somewhere
> less trusted, put Spring Security in front of `/h2-console/**` and
> `/actuator/**` first — see
> [Before Adding Spring Security](SECURITY.md#before-adding-spring-security).
>
> See [`SECURITY.md`](SECURITY.md) for the related Actuator exposure notes.

------------------------------------------------------------------------

## Testing

Unit tests (`*Test.java`, fast, run via Surefire) and integration tests
(`*IT.java`, boot the full app and hit it over real HTTP via Failsafe) are
kept separate:

    # Unit tests only
    mvn test

    # Unit tests + integration tests (also runs during CI)
    mvn verify

| Suite | Phase | What it covers |
| ----- | ----- | -------------- |
| `TaskControllerUnitTest` | `test` | HTTP surface with `TaskService` mocked: status codes, `Location` header, problem+json bodies, request validation, sort screening. |
| `TaskServiceTest` | `test` | The write rules with the repository mocked — not-found reporting, the version precondition, and create's insert-only guarantee, none of which a controller test can observe directly. |
| `SortablePropertiesTest` | `test` | Reflection check that every allowlisted sort key is a real `Task` field, so renaming one fails the build rather than the endpoint. |
| `TaskEqualityTest` | `test` | The entity identity contract — hash stability across `persist()`, identity by ID only. |
| `TaskDtoTest` | `test` | Bean Validation boundaries (null/empty/blank/at-limit/over-limit) and entity→DTO mapping, driven through `Validator` directly. |
| `TaskApiIT` | `verify` | Full CRUD over real HTTP, pagination boundaries, page-size cap, problem+json error shapes. |
| `TaskPersistenceIT` | `verify` | Real Hibernate behaviour: version increments, stale-write rejection, schema constraints, paging and sorting. |
| `TaskConcurrencyIT` | `verify` | Eight simultaneous writers against one task — exactly one 200, the rest 409, one version increment. |

Coverage reports land in `target/site/jacoco/` (unit) and
`target/site/jacoco-it/` (integration) after `mvn verify`; open
`index.html` in either to see line and branch coverage.

------------------------------------------------------------------------

## Code Quality (SonarQube Cloud)

Every push/PR to `main` runs a `code-quality` job (`.github/workflows/ci.yml`)
that scans the codebase with [SonarQube Cloud](https://sonarcloud.io) for
bugs, vulnerabilities, code smells, and duplication, and reports test
coverage (via the JaCoCo report `pom.xml` now generates during `mvn verify`).
Results show up as a commit status check and, on PRs, as inline review
comments.

**One-time setup** (only needs to be done once per repo, by a repo admin):

1.  Sign up at [sonarcloud.io](https://sonarcloud.io) (free for this
    project's size - up to 50k lines of code on the free tier) and import
    this GitHub repository as a new project.
2.  Under **Administration > Analysis Method**, turn off *Automatic
    Analysis* - the CI workflow does CI-based analysis instead, which is
    required to get coverage data in.
3.  Generate a token under **My Account > Security**.
4.  In the GitHub repo, under **Settings > Secrets and variables >
    Actions > Secrets**, add:
    -   `SONAR_TOKEN` - the token from step 3.
    -   `SONAR_ORGANIZATION` - your SonarQube Cloud organization key.
    -   `SONAR_PROJECT_KEY` - the project key assigned in step 1.

Until these are configured, the `code-quality` job will fail (or can be
disabled by removing it from `ci.yml`).

To run the same scan locally: `mvn verify sonar:sonar
-Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=<org>
-Dsonar.projectKey=<key> -Dsonar.token=<your personal token>`.

------------------------------------------------------------------------

## Continuous Integration & Security

-   **`.github/workflows/ci.yml`** builds and runs the full test suite
    (`mvn verify`) with **JDK 25** on every push to `main` and on every
    pull request, then does a validation-only Docker build, then runs the
    SonarQube Cloud scan described above.
-   **`.github/workflows/codeql.yml`** runs CodeQL static analysis on
    every push/PR and weekly on a schedule.
-   **Dependabot** (`.github/dependabot.yml`) opens weekly PRs for
    outdated Maven dependencies, GitHub Actions, and the Docker base
    image. See [`SECURITY.md`](SECURITY.md) for the vulnerability
    reporting process.
