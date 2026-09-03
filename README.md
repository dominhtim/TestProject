# Task API

A To-Do task CRUD service built with **Spring Boot 4.1**, **Java 25**, **Spring Data JPA**
and an **H2 in-memory database**. Paginated listing, optimistic locking, RFC 9457 error
responses, and a local observability stack.

## Quick start

Requires **JDK 25** and **Maven 3.8+**.

```bash
mvn spring-boot:run
```

The app starts on **http://localhost:8080**. Interactive docs are at
**/swagger-ui.html**; the OpenAPI spec is at **/v3/api-docs**.

Each request runs on a virtual thread, so the blocking JPA/JDBC code scales without a
reactive rewrite. That makes the JDBC pool the real concurrency limit, which is why
`spring.datasource.hikari.maximum-pool-size` is raised to 50.

To run it with the full observability stack instead:

```bash
docker compose up --build
```

## API

Base URL: **http://localhost:8080/api/v1/tasks**

| Method | Path | Description | Request body | Responses |
| --- | --- | --- | --- | --- |
| `POST` | `/` | Create a task | `{ "title": "New task" }` | 201 + `Location`, 400 |
| `GET` | `/` | List tasks (paginated) | – | 200 |
| `GET` | `/{id}` | Fetch one task | – | 200, 400, 404 |
| `PUT` | `/{id}` | Replace a task | `{ "title": "…", "completed": true, "version": 0 }` | 200, 400, 404, 409 |
| `DELETE` | `/{id}` | Delete a task | – | 204, 404, 409 |

`id` and `version` are server-owned; an `id` sent on a create is ignored.

### Pagination

`GET /api/v1/tasks` accepts `page`, `size` and `sort`, and returns Spring Data's
`PagedModel` envelope:

```json
{
  "content": [ { "id": 1, "title": "Buy groceries", "completed": false, "version": 0 } ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

Default page size is 20; the maximum a client may request is 100. `sort` accepts `id`,
`title` and `completed` — any other property returns 400.

### Optimistic locking

Every task carries a `version`. Send the version you last read in a `PUT` and the request is
rejected with **409 Conflict** if the task changed in the meantime; re-read it and retry.

Omitting `version` waives that check for stale reads, but does **not** disable optimistic
locking — two genuinely concurrent writers can both omit it and the second to commit still
gets a 409. **Any client issuing a `PUT` or `DELETE` should handle 409**, whether or not it
sends a version.

### Errors

Errors come back as RFC 9457 `application/problem+json`:

```json
{
  "type": "https://api.example.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "errors": { "title": "Title is mandatory" }
}
```

Exception messages are never echoed into responses — they go to the logs, correlated by
trace ID. Failures occurring before a request reaches a handler method (in a servlet filter,
say) bypass this and return a bare response from Spring Boot's default error handling.

## Project layout

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

A second resource is a new package under `com/example/`, not an edit to six layer packages.
Tests mirror the same structure.

Contributors should also read [`CLAUDE.md`](CLAUDE.md), which documents the architecture and
the reasoning behind the less obvious decisions.

## Testing

```bash
mvn test          # unit tests (*Test.java) — fast
mvn verify        # unit + integration tests (*IT.java), as CI runs them
```

A single test or method:

```bash
mvn test -Dtest=TaskServiceTest
mvn test -Dtest=TaskServiceTest#updateRejectsStaleVersion
mvn verify -Dit.test=TaskConcurrencyIT      # integration tests use -Dit.test
```

| Suite | Phase | Covers |
| --- | --- | --- |
| `TaskControllerUnitTest` | `test` | HTTP surface with the service mocked: status codes, headers, problem+json bodies, validation, sort screening |
| `TaskServiceTest` | `test` | Write rules: not-found reporting, the version precondition, insert-only create |
| `GlobalExceptionHandlerTest` | `test` | Handler branches not reachable over HTTP |
| `SortablePropertiesTest` | `test` | Sort allowlist matches real `Task` fields |
| `TaskEqualityTest` | `test` | Entity identity contract |
| `TaskDtoTest` | `test` | Validation boundaries and entity→DTO mapping |
| `TaskApiIT` | `verify` | Full CRUD over real HTTP, pagination, error shapes |
| `TaskPersistenceIT` | `verify` | Real Hibernate: version increments, stale writes, schema constraints |
| `TaskConcurrencyIT` | `verify` | Eight simultaneous writers against one task |

Coverage reports land in `target/site/jacoco/` and `target/site/jacoco-it/` after
`mvn verify` — open `index.html` in either.

## Observability

Instrumented with Actuator, Micrometer and OpenTelemetry.

| What | Where |
| --- | --- |
| Health | `/actuator/health` |
| App info | `/actuator/info` |
| Prometheus metrics | `/actuator/prometheus` |
| Logs | Console, JSON (ECS format); shipped to Loki via Alloy in the Docker stack |
| Traces | Exported via OTLP to Jaeger |

Only `health`, `info`, `prometheus` and `metrics` are exposed. Read
[`SECURITY.md`](SECURITY.md) before exposing more.

### The local stack

`docker compose up --build` starts the app alongside Prometheus, Jaeger, Loki + Alloy and
Grafana:

| Service | URL |
| --- | --- |
| App | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Prometheus | http://localhost:9090 |
| Jaeger | http://localhost:16686 |
| Alloy | http://localhost:12345 |
| Grafana | http://localhost:3000 (`admin` / `admin`) |

Grafana ships with Prometheus and Loki datasources pre-provisioned. In Explore, query
`{container="testproject-app-1"}` for the app's logs, or add `| json | status >= 500` to
filter to errors.

To run the app on your host with only the tooling in Docker: start the other services
(`docker compose up prometheus jaeger loki alloy grafana`) and change the target in
[`observability/prometheus.yml`](observability/prometheus.yml) from `app:8080` to
`host.docker.internal:8080`. Alloy then collects logs from the containers only, not from the
app on your host.

## Database console

The H2 console is **disabled by default** and lives in the `dev` profile, which
`docker compose up` sets for you. To get it when running directly:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

It is then at **http://localhost:8080/h2-console** — JDBC URL `jdbc:h2:mem:taskdb`,
user `sa`, password `password`.

> ⚠️ **Local use only.** The console is an unauthenticated SQL client, and the `dev` profile
> also disables H2's own local-only check. Never enable this profile on a network you do not
> fully control. See [`SECURITY.md`](SECURITY.md).

## Continuous integration

- **`.github/workflows/ci.yml`** — builds and runs the full suite (`mvn verify`) on JDK 25
  for every push to `main` and every PR, then a validation-only Docker build, then the
  SonarQube scan.
- **`.github/workflows/codeql.yml`** — CodeQL analysis on every push/PR and weekly.
- **Dependabot** — weekly PRs for Maven dependencies, GitHub Actions and the Docker base
  image.

### SonarQube Cloud setup

The `code-quality` job needs three repository secrets and fails until they are configured
(or until the job is removed from `ci.yml`). One-time setup, by a repo admin:

1. Sign up at [sonarcloud.io](https://sonarcloud.io) and import this repository.
2. Under **Administration → Analysis Method**, turn off *Automatic Analysis* — CI-based
   analysis is what gets coverage data in.
3. Generate a token under **My Account → Security**.
4. Add `SONAR_TOKEN`, `SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` under **Settings →
   Secrets and variables → Actions**.

To run the same scan locally:

```bash
mvn verify sonar:sonar -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.organization=<org> -Dsonar.projectKey=<key> -Dsonar.token=<token>
```

## Known issues

`springdoc-openapi` still generates its spec using Jackson 2 internally on Spring Boot 4 /
Jackson 3 ([upstream issue](https://github.com/springdoc/springdoc-openapi/issues/3268)).
If `/swagger-ui.html` or `/v3/api-docs` throws a Jackson error, that is why.
