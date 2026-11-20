<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Contributing

## Cohesive changes

Keep each change focused on one user-visible behavior or maintenance
concern. Include the code, docs, and focused verification needed to make
that behavior understandable and reviewable. Stage only the intended
paths; never `git add .`, never `git commit -a`. Prefer single-file commits
where the change allows.

Commit messages use `<type>(<scope>): <subject>` with the subject in
imperative mood, lowercase, no period, max 72 characters. Explain why
in the body, not what.

## Development setup

- JDK 25 on `PATH` (`java -version` must say 25).
- Node.js 24 or newer for the browser workbench.
- pnpm 12.14.0 from an existing installation. Bootstrap never installs
  Node, Java, pnpm, or system packages: no global installs. Use the Maven
  wrapper (`./mvnw` / `mvnw.cmd`) and `dev.ps1`.
- Maven and pnpm caches live under the ignored repository `.cache/`;
  `.\dev.ps1 cache:report` shows their sizes and `cache:prune --apply`
  removes listed paths.
- Configure at least one sign-in provider. The lean canonical basis-auth
  keys are `BASIS_AUTH_ISSUER`, `BASIS_AUTH_CLIENT_ID`,
  `BASIS_AUTH_CLIENT_SECRET`, `BASIS_AUTH_RESOURCE`,
  `BASIS_AUTH_RESOURCE_SECRET`, `BASIS_AUTH_INTERNAL_URL`,
  `BASIS_AUTH_COOKIE_SECRET` (at least 32 bytes), and `CURRENT_URL_ORIGIN`;
  the callback is `${CURRENT_URL_ORIGIN}/api/auth/basis/callback` and the
  backchannel receiver is `${CURRENT_URL_ORIGIN}/api/auth/basis/backchannel`.
  Legacy `BASIS_*` names are aliases only, and the app uses SQLite by
  default. See the [downstream contract](docs/basis_auth_setup_guide.md).
  Legacy `BASIS_REDIRECT_URI` / `BASIS_CLIENT_ID` / `BASIS_CLIENT_SECRET` /
  `BASIS_RESOURCE` names and `POST /api/auth/backchannel` remain accepted
  aliases, but the canonical contract above is preferred for new deployments.

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

## Test commands

| Command | Checks |
|---|---|
| `.\dev.ps1 test` | focused Maven checks for changed behavior |
| `./mvnw -B verify -Psmoke` | full Linux verify: format, license, ArchUnit, tests, packaging, packaged-jar browser smoke |
| `pnpm --dir web run format:check` | `oxfmt` check for the SolidJS workbench |
| `pnpm --dir web test:e2e` | Playwright E2E against the local stub, including the iPad viewport/touch matrix |
| `.\dev.ps1 smoke` | smoke the packaged jar on Linux |

Run focused tests for changed behavior and the full verify before a release.
The recorded gate status lives in
[`docs/operations/release-acceptance.md`](docs/operations/release-acceptance.md).

## Style

- New and touched Java stays at AP Computer Science A level: plain and
  abstract classes and constructs teachable in AP CSA. The build still uses
  JDK 25 and targets Java 25 bytecode.
- Use 4-space indentation in Java and TypeScript. TypeScript is formatted
  with `oxfmt`; every file carries the license header.
- Handwritten user-facing errors: no stack traces or internal names, a
  machine-readable code from `docs/reference/errors.md`, and a correlation id
  pointing at the server log.

## Platform contract

- The same single jar runs on Linux, macOS, and Windows; Linux x64 and arm64
  are the CI-verified targets, and real-host acceptance on macOS and Windows
  is still open.
- iPad Safari, Chrome, and Edge are primary browser clients; desktop browsers
  use the same workbench.
- POSIX, macOS, and Windows process behavior stays behind the platform SPI;
  persistent application data lives under the jar-adjacent data directory.
- Workspace processes share the service account; do not claim untrusted-user
  isolation.

## Release checklist

1. `.\dev.ps1 test` green.
2. Linux x64 CI builds, verifies, packages, and launches the jar; macOS and
   Windows run the frontend format check and Maven tests.
3. Native Linux arm64 acceptance passes before release.
4. `docs/operations/release-acceptance.md` gate status and
   `docs/operations/acceptance.md` results table are filled with the commit
   SHA.
5. No new top-level dependency without an ADR in
   `docs/contributing/architecture-decisions/`.

## Pull requests

- One cohesive behavior per PR with a link to the issue it addresses; prefer
  single-file commits inside the PR and stage only intended paths.
- State the user-visible change and how you verified it. Include new or
  changed tests and license headers on new files.
- Update `docs/operations/acceptance.md` and
  `docs/operations/release-acceptance.md` in the same PR when a gate,
  workflow, or test command changes. Green CI alone does not close the
  physical-iPad or live real-host gates.
- No new top-level dependency without an ADR in
  `docs/contributing/architecture-decisions/`.

## Error-message checklist

- Handwritten message; never a stack trace or an internal name
  (no `Firecracker`, `KVM`, `Btrfs`, `cgroups`, `Spring`).
- Machine-readable code from `docs/reference/errors.md`.
- Correlation id pointing at the server log.
- Review the wording; errors are UI.

## Acceptance

The release bar is the human-run checklist in
[`docs/operations/acceptance.md`](docs/operations/acceptance.md) together with
the automated gates recorded in
[`docs/operations/release-acceptance.md`](docs/operations/release-acceptance.md).
Automated gates are necessary but do not replace the physical-iPad and live
real-host sign-offs. When a gate changes, update both records in the same
change.

## Legal

By contributing you agree your work is licensed under the Apache
License 2.0 and you have the right to submit it (Developer Certificate
of Origin 1.1).
