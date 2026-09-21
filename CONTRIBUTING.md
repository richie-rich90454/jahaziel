<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Contributing

## One file, one commit

Each commit holds one logical change to one file. Stage with
`git add <exact-file>`; never `git add .`, never `git commit -a`.

Commit messages use `<type>(<scope>): <subject>` with the subject in
imperative mood, lowercase, no period, max 72 characters. Explain why
in the body, not what.

## Prerequisites

- JDK 25 on `PATH` (`java -version` must say 25).
- Node 24.18 for the IDE build (`node --version` must say v24.18.x).
- No other runtime is required to build the jar.

## Dev verbs

| Verb | Command | When |
|---|---|---|
| `init` | `java -jar jahaziel.jar init` | scaffold `.env` beside the jar |
| `run` | `java -jar jahaziel.jar` | serve `http://localhost:11451` |
| `build` | `./mvnw -q -DskipTests package` | produce `target/jahaziel.jar` |
| `test` | `./mvnw verify` | full checks before every commit |
| `smoke` | `./mvnw verify -Psmoke` | portable smoke on a release combo |

## Cross-platform rules

- No `os.name` (or any OS branch) outside `dev.jahaziel.platform`.
  Backends vary behind the `NodeBackend` SPI, never in callers.
- Only portable JDK APIs in shared code; POSIX/Win32 specifics stay
  behind the platform boundary.
- The runtime writes only under `./data/`. Never recommend a path
  outside it.

## Testing

Run `./mvnw verify` before every commit. Never commit a broken build.
Release candidates additionally pass `./mvnw verify -Psmoke` on each
of the six OS/arch combos in `docs/plan.md`. Every file carries the
license header; Java code follows google-java-format. Both are checked
by the build.

## Release checklist

1. `./mvnw verify` green.
2. `-Psmoke` green on all six OS/arch combos, by a human (no CI).
3. `docs/operations/acceptance.md` results table filled with the
   commit SHA.
4. No new top-level dependency without an ADR in
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
