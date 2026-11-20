<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Verification

After any deploy or upgrade, on the release hardware:

1. `http://localhost:11451/actuator/health` returns 200.
2. Log in, create a workspace, open the IDE, run a command.
3. Open a workspace port through the gateway and confirm it serves.
4. Create and restore a snapshot; confirm the files come back.
5. Confirm nothing was written outside `./data/`.

## Automated checks

Run these from the repository root with the repo-local toolchain; no
global installs are needed.

| Check | Command | Current result |
|---|---|---|
| Maven build and tests | `./mvnw -B verify -Psmoke` | 1942 tests, all passing |
| Workbench E2E | `pnpm --dir web test:e2e` | 60 passed / 1 skipped (Playwright against the stub, including the iPad viewport/touch matrix) |
| License and format | Maven license/format goals and `pnpm --dir web run format:check` | clean |
| CI workflows | [linux.yml](../../.github/workflows/linux.yml), [cross-platform.yml](../../.github/workflows/cross-platform.yml), [deployment-acceptance.yml](../../.github/workflows/deployment-acceptance.yml) | all three green |

The E2E suite is a regression check against a stub; it does not replace the
physical-iPad or live-host gates in the
[release acceptance record](./release-acceptance.md).

Failures carry a code plus a correlation id; look the code up in
[errors](../reference/errors.md) and check the server log under
`./data/logs/` with `java -jar jahaziel.jar logs`. For a bug report, collect
a redacted support bundle as described in
[Update and rollback](./update-and-rollback.md#bug-report-collection).
