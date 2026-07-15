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

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it
privately rather than opening a public issue:

1. Use [GitHub's private vulnerability reporting](../../security/advisories/new) for this repository (Security tab → "Report a vulnerability"), or
2. Email the maintainer directly if the advisory form isn't available.

Please include a description of the vulnerability, steps to reproduce it,
and the potential impact. We aim to acknowledge reports within a few
business days. Once a fix is available, we'll coordinate on disclosure
timing before making details public.
