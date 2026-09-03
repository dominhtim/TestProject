# Security Policy

## Supported Versions

This project is a single continuously-developed application, not a library with
maintained release lines. Security fixes land on the latest commit on `main`.

| Version | Supported |
| --- | --- |
| `main` (latest) | :white_check_mark: |
| Older commits/tags | :x: |

## Reporting a Vulnerability

Please report security issues privately rather than opening a public issue:

1. Use [GitHub's private vulnerability reporting](../../security/advisories/new) for this
   repository (**Security** tab → *Report a vulnerability*), or
2. Email the maintainer directly if the advisory form is unavailable.

Include a description, steps to reproduce and the potential impact. We aim to acknowledge
reports within a few business days, and will coordinate on disclosure timing once a fix is
available.

## Before You Deploy This

**This application has no authentication.** Spring Security is not on the classpath, so
every HTTP endpoint it serves is reachable by anyone who can reach the port. It is built as
a demo and is safe to run locally; putting it on an untrusted network requires the
mitigations below first.

### The `dev` profile

The H2 web console is disabled by default and confined to the `dev` profile
(`application-dev.properties`), which `docker-compose.yml` sets for the local stack. The
image built by the `Dockerfile` starts with the console off unless `SPRING_PROFILES_ACTIVE=dev`
is passed in.

Do not enable that profile on any network you do not fully control. The console is not a
read-only viewer for this app's database — it is a general-purpose H2 client that accepts an
arbitrary JDBC url, user and password, so unauthenticated access to it should be treated as
a remote code execution risk rather than a data disclosure one. The profile also sets
`spring.h2.console.settings.web-allow-others=true`, which disables H2's own local-only
check; that exists to work around Docker's port forwarding and is not a security control.

If you need the console anywhere less trusted, put Spring Security in front of
`/h2-console/**` first.

### Actuator endpoints

`management.endpoints.web.exposure.include` is deliberately a short allowlist —
`health,info,prometheus,metrics` — with `management.endpoint.health.show-details=never`.
Never set it to `*`.

Adding an endpoint that returns sensitive data (`env`, `configprops`, `heapdump`) means
publishing that data to anyone who can reach the app. Put Spring Security in front of
`/actuator/**` before doing so.

The project also declares `spring-boot-health` explicitly, as defence in depth per the
advisory for [CVE-2026-40976](https://spring.io/security/cve-2026-40976/) (an Actuator
filter-chain bypass), although the Spring Boot version in use is already patched.

### Adding Spring Security

If you add `spring-boot-starter-security`, `GlobalExceptionHandler` needs handlers for
`AccessDeniedException` and `AuthenticationException` at the same time. Without them both
fall into the catch-all and are reported as 500 rather than 403 and 401, with an ERROR-level
stack trace logged for what is a routine authorization outcome. See [`CLAUDE.md`](CLAUDE.md)
for the details.

## Dependency and Code Scanning

- **Dependabot** ([`.github/dependabot.yml`](.github/dependabot.yml)) opens weekly pull
  requests for outdated or vulnerable Maven dependencies, GitHub Actions and the Docker base
  image.
- **CodeQL** ([`.github/workflows/codeql.yml`](.github/workflows/codeql.yml)) runs on every
  push and pull request to `main`, plus a weekly scheduled scan.
- **SonarQube Cloud** scans for bugs, vulnerabilities and code smells on every push and PR.
