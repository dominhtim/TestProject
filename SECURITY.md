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

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it
privately rather than opening a public issue:

1. Use [GitHub's private vulnerability reporting](../../security/advisories/new) for this repository (Security tab → "Report a vulnerability"), or
2. Email the maintainer directly if the advisory form isn't available.

Please include a description of the vulnerability, steps to reproduce it,
and the potential impact. We aim to acknowledge reports within a few
business days. Once a fix is available, we'll coordinate on disclosure
timing before making details public.
