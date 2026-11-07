<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Contributing

## Cohesive changes

Keep each change focused on one user-visible behavior or maintenance
concern. Include the code, docs, and focused verification needed to make
that behavior understandable and reviewable. Stage only the intended
paths; never `git add .`, never `git commit -a`.

Commit messages use `<type>(<scope>): <subject>` with the subject in
imperative mood, lowercase, no period, max 72 characters. Explain why
in the body, not what.

## Prerequisites

- JDK 25 on `PATH` (`java -version` must say 25).
- Node.js 24 or newer for the browser workbench.
- pnpm 12.14.0, invoked from an existing installation. Bootstrap
  never installs Node, Java, pnpm, or system packages.
- Maven and pnpm caches are configured under the ignored repository `.cache/`.

## Dev verbs

| Verb | Command | When |
|---|---|---|
| `init` | `java -jar jahaziel.jar init` | scaffold `.env` beside the jar |
| `run` | `java -jar jahaziel.jar` | serve `http://localhost:11451` |
| `build` | `.\dev.ps1 build` | build `web/src`, then produce `target/jahaziel.jar` |
| `test` | `.\dev.ps1 test` | run the focused project checks |
| `smoke` | `.\dev.ps1 smoke` | smoke the packaged jar on Linux |
| `cache:report` | `.\dev.ps1 cache:report` | show local dependency and build-output sizes |
| `cache:prune` | `.\dev.ps1 cache:prune` | preview generated cache/output cleanup; `--apply` removes only listed paths |

## Platform contract

- Jahaziel servers run on Linux x64 and arm64. Windows/macOS are browser clients.
- POSIX process and PTY behavior stays in the backend and terminal seams.
- Persistent application data is under the configured data directory.
- Workspace processes share the service account; do not claim untrusted-user isolation.

## Testing

Run focused tests for changed behavior and `.\dev.ps1 test` before release.
Linux x64 CI builds, tests, packages, and launches the jar. Release
candidates also require a native Linux arm64 acceptance run. Every file
carries the license header; Java code follows google-java-format.

## Release checklist

1. `.\dev.ps1 test` green.
2. Linux x64 CI builds, verifies, packages, and launches the jar.
3. Native Linux arm64 acceptance passes before release.
4. `docs/operations/acceptance.md` results table filled with the
   commit SHA.
5. No new top-level dependency without an ADR in
   `docs/contributing/architecture-decisions/`.

## Error-message checklist

- Handwritten message; never a stack trace or an internal name
  (no `Firecracker`, `KVM`, `Btrfs`, `cgroups`, `Spring`).
- Machine-readable code from `docs/reference/errors.md`.
- Correlation id pointing at the server log.
- Review the wording; errors are UI.

## Acceptance

The release bar is `docs/operations/acceptance.md`: a human-run
checklist plus a results table. No automation stands in for it.

## Legal

By contributing you agree your work is licensed under the Apache
License 2.0 and you have the right to submit it (Developer Certificate
of Origin 1.1).
