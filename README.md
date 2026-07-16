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

## API Endpoints (CRUD)

All endpoints use the base URL:\
**http://localhost:8080/api/v1/tasks**

  ----------------------------------------------------------------------------------------------------------
  Method   Path      Description      Request Body (JSON)                                 Response Status
  -------- --------- ---------------- --------------------------------------------------- ------------------
  POST     `/`       Create a new     `{ "title": "New Task Title" }`                     200 OK
                     task.                                                                

  GET      `/`       Retrieve all     \-                                                  200 OK
                     tasks.                                                               

  GET      `/{id}`   Retrieve a       \-                                                  200 OK or 404 Not
                     single task by                                                       Found
                     ID.                                                                  

  PUT      `/{id}`   Update an        `{ "title": "Updated Title", "completed": true }`   200 OK or 404 Not
                     existing task.                                                       Found

  DELETE   `/{id}`   Delete a task by \-                                                  204 No Content or
                     ID.                                                                  404 Not Found
  ----------------------------------------------------------------------------------------------------------

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
| Structured logs      | Console, JSON (ECS format)                   |
| Distributed tracing  | Exported via OTLP to Jaeger                  |

Only `health`, `info`, `prometheus`, and `metrics` are exposed
(`management.endpoints.web.exposure.include` in `application.properties`) -
this app has no Spring Security configured, so anything exposed here is
reachable by anyone. See [`SECURITY.md`](SECURITY.md) for more on this.

### Running the full observability stack locally

```
docker compose up --build
```

This builds the app image and starts it alongside Prometheus, Jaeger, and
Grafana, all wired together:

-   **App:** http://localhost:8080
-   **Prometheus:** http://localhost:9090
-   **Jaeger UI:** http://localhost:16686
-   **Grafana:** http://localhost:3000 (login `admin` / `admin`, Prometheus
    datasource is pre-provisioned)

If you'd rather run the app directly (`mvn spring-boot:run`) and only the
observability tools in Docker, start just those three services
(`docker compose up prometheus jaeger grafana`) and change the target in
[`observability/prometheus.yml`](observability/prometheus.yml) from
`app:8080` to `host.docker.internal:8080`.

------------------------------------------------------------------------

## Database Console

The H2 in-memory database console is available for easy viewing at:\
**http://localhost:8080/h2-console**

**JDBC URL:** `jdbc:h2:mem:taskdb`\
**Username:** `sa`\
**Password:** `password`

------------------------------------------------------------------------

## Testing

Unit tests (`*Test.java`, fast, run via Surefire) and integration tests
(`*IT.java`, boot the full app on a random port and hit it over real HTTP
via Surefire/Failsafe) are kept separate:

    # Unit tests only
    mvn test

    # Unit tests + integration tests (also runs during CI)
    mvn verify

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
    Actions**, add:
    -   Secret `SONAR_TOKEN` - the token from step 3.
    -   Variable `SONAR_ORGANIZATION` - your SonarQube Cloud organization key.
    -   Variable `SONAR_PROJECT_KEY` - the project key assigned in step 1.

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
