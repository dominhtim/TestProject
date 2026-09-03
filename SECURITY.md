# Security Policy

## Supported Versions

This project is a single, continuously-developed application rather than a
library with multiple maintained release lines. Security fixes are applied
to the latest commit on the `main` branch; there are no older maintained
branches.

| Version         | Supported          |
| ---------------- | ------------------ |
| `main` (latest)  | :white_check_mark: |
| Older commits/tags | :x:               |

## Dependency & Code Scanning

- **Dependabot** is enabled ([`.github/dependabot.yml`](.github/dependabot.yml)) and opens weekly pull requests for outdated or vulnerable Maven dependencies, GitHub Actions, and the Docker base image.
- **CodeQL** static analysis runs on every push and pull request to `main`, plus a weekly scheduled scan ([`.github/workflows/codeql.yml`](.github/workflows/codeql.yml)).

## Actuator Endpoint Exposure

This app has no Spring Security configured, so any Actuator endpoint that's
exposed over HTTP is reachable by anyone with network access - there's no
authentication layer to gate it. `management.endpoints.web.exposure.include`
in `application.properties` is deliberately kept to a short allowlist
(`health,info,prometheus,metrics`), not `*`. Spring Boot's default web
security filter chain had a critical bypass affecting Actuator specifically
([CVE-2026-40976](https://spring.io/security/cve-2026-40976/), fixed in
4.0.6) that made this kind of overexposure easy to hit by accident; this
project is on 4.1.0 (patched) and also declares `spring-boot-health`
explicitly per the advisory's defense-in-depth guidance. If you add
Actuator endpoints that return sensitive data (`env`, `configprops`,
`heapdump`, etc.) or deploy this beyond local/demo use, put Spring Security
in front of `/actuator/**` first.

## H2 Console Exposure

The H2 web console is **not enabled by default**. It is confined to the `dev`
profile ([`application-dev.properties`](src/main/resources/application-dev.properties)),
which `docker-compose.yml` sets for the local stack; the image the
`Dockerfile` builds starts with it off unless `SPRING_PROFILES_ACTIVE=dev` is
passed in.

This matters more than "a console for the demo database" suggests. H2's
console is a generic JDBC client: its login form accepts an arbitrary url,
user, password and driver, so it is not scoped to `taskdb`, and reaching it
unauthenticated is a code-execution-class exposure rather than a
data-disclosure one. The `dev` profile also sets
`spring.h2.console.settings.web-allow-others=true`, which disables H2's own
local-only check — needed because Docker's port forwarding makes requests
arrive via NAT rather than loopback, but it is a workaround, not a mitigation.

The runtime image (`eclipse-temurin:25-jre-alpine`) ships no `javac`, which
closes the H2 alias path that compiles Java source at runtime. Aliases bound
to existing static methods do not need a compiler, so treat this as narrowing
the surface, not removing it.

Do not enable the `dev` profile on any network you do not fully control. If
the console is ever needed somewhere less trusted, put Spring Security in
front of `/h2-console/**` first.

## Before Adding Spring Security

`GlobalExceptionHandler` ends with a catch-all `@ExceptionHandler(Exception.class)`
that maps anything unclassified to a 500. Exceptions carrying their own HTTP
status are matched by more specific handlers first, so this is correct today —
but Spring Security's `AccessDeniedException` carries no status and implements
no marker interface, so it would land in the catch-all and be reported as
**500 instead of 403**, with a stack trace logged at ERROR for what is a
routine authorization outcome.

The handler cannot be written before the class is on the classpath. When you
add `spring-boot-starter-security`, add this at the same time:

```java
@ExceptionHandler(AccessDeniedException.class)
public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
    // 403, and deliberately no detail about what was being protected.
}
```

`AuthenticationException` needs the same treatment for 401.

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it
privately rather than opening a public issue:

1. Use [GitHub's private vulnerability reporting](../../security/advisories/new) for this repository (Security tab → "Report a vulnerability"), or
2. Email the maintainer directly if the advisory form isn't available.

Please include a description of the vulnerability, steps to reproduce it,
and the potential impact. We aim to acknowledge reports within a few
business days. Once a fix is available, we'll coordinate on disclosure
timing before making details public.
