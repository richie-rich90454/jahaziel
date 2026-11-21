<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Release acceptance record

This record tracks the release-acceptance gates from the
[delivery plan](../plan.md#acceptance-gates). It complements the
[release acceptance checklist](./acceptance.md). Green CI is necessary but not
sufficient for release: every gate below must be signed off.

## Gate status

### Gate 1 — CI green on all release targets: Passing

The packaged jar is built and verified on five targets. Linux runs the full
`verify` lifecycle (format, license, ArchUnit, tests, packaging, and a
packaged-jar browser smoke) plus the real Playwright E2E suite; macOS and
Windows run the frontend format check and the Maven test suite. Native
architecture is asserted directly (`x86_64` and `aarch64`); emulation does not
satisfy the arm64 target. The Windows x64 leg was broken because the
extensionless `mvnw` wrapper checked out with CRLF and could not run under
`bash`; `.gitattributes` now pins it to LF and the leg runs again. Workflow
actions are pinned to immutable commit SHAs rather than floating tags.

| Target | Runner | Verification | Result |
|---|---|---|---|
| Linux x64 | ubuntu-26.04 (`x86_64`) | [`./mvnw -B verify -Psmoke`](../../.github/workflows/linux.yml), `pnpm --dir web exec playwright test`, and `node tools/browser-smoke.cjs target/jahaziel.jar` | Passing |
| Linux arm64 | ubuntu-26.04-arm (`aarch64`) | same as x64, on a native arm64 runner | Passing |
| macOS arm64 | macos-15 | `pnpm --dir web run format:check` and `./mvnw -B test -Dfmt.skip=true -Dlicense.skip=true` ([cross-platform.yml](../../.github/workflows/cross-platform.yml)) | Passing |
| macOS x64 | macos-15-intel | same as macOS arm64 | Passing |
| Windows x64 | windows-2025 | `pnpm --dir web run format:check` and `mvnw.cmd -B test -Dfmt.skip=true -Dlicense.skip=true`; the deployment leg's bash `./mvnw` is fixed by `.gitattributes` | Passing |

Recorded counts for the current green run:

- Maven: **1943 unit tests (16 skipped) + 5 smoke tests**, all passing (`./mvnw -B verify -Psmoke`).
- E2E: **60 passed / 1 skipped** (`pnpm --dir web exec playwright test`, Playwright against the stub, including the iPad viewport/touch matrix; now run by Linux CI).
- License and format checks: **clean** (Maven license/format goals and `pnpm --dir web run format:check`).

### Gate 2 — Automated deployment walkthrough: Passing

The scripted walkthrough in
[`tools/deployment-acceptance.cjs`](../../tools/deployment-acceptance.cjs)
runs on all five host targets via
[deployment-acceptance.yml](../../.github/workflows/deployment-acceptance.yml).
It verifies 19 checks: demo sign-in, identity, templates, workspace setup,
files and ETag, snapshots, tasks, preview candidates, admin diagnostics,
logout, backup, restore, and a fresh-boot readback. It cleans up all child
processes and scratch directories.

| Host | Status |
|---|---|
| Ubuntu x64 | Passing |
| Ubuntu arm64 | Passing |
| macOS x64 | Passing |
| macOS arm64 | Passing |
| Windows x64 | Passing |

Note: the restore drill restores into a fresh scratch data directory because
read-only snapshots make in-place `tar` extraction fail on Windows; a real-host
in-place restore with snapshots is therefore still exercised only by Gate 4.

### Gate 3 — Physical iPad matrix: Not verified

Real-device acceptance is required; desktop browser automation does not replace
it. Cover Safari, Chrome, and Edge on the current and two previous major
iPadOS versions, with touch-only and keyboard/trackpad input, in portrait and
landscape, and in Split View and Stage Manager. No device results are recorded.

### Gate 4 — Live host deployment with IdP MFA: Not verified

Deploy the packaged jar on real Ubuntu, macOS, and Windows machines with the
exact IDE and preview DNS records, TLS, identity-provider login with MFA, a
project task, and a working path-based preview. No real-host results are recorded.

## Sign-off

| Gate | Evidence | Status | Owner | Date |
|---|---|---|---|---|
| CI green on Linux x64/arm64 and macOS x64/arm64, Windows x64 | [linux.yml](../../.github/workflows/linux.yml), [cross-platform.yml](../../.github/workflows/cross-platform.yml); Maven 1943 unit tests (16 skipped) + 5 smoke, E2E 60 passed / 1 skipped, license/format clean, Windows bash wrapper fixed | Passing | CI | — |
| Automated deployment walkthrough on Ubuntu x64/arm64, macOS x64/arm64, Windows x64 | [deployment-acceptance.yml](../../.github/workflows/deployment-acceptance.yml), 19 checks per host | Passing | CI | — |
| Physical iPad browser matrix | Real-device results (none recorded) | Open | — | — |
| Live host deployment with IdP MFA | Real-host results (none recorded) | Open | — | — |

## Release statement

The repository is deployable and CI-verified across the targets above, and the
automated per-host deployment walkthrough is green. It is **not
release-accepted** until the physical iPad and live real-host gates are signed
off. Treat the open gates as blockers, not follow-up work.
