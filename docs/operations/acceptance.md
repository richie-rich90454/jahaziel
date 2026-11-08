<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Release acceptance

Release acceptance covers a Linux x64 server and a native Linux arm64 server.
Windows, macOS, and Linux desktop browsers are clients. Linux x64 and arm64 are
built, tested, packaged, and smoke-launched on native CI runners. The arm64 job
must report `aarch64`; emulation does not substitute for it. Both jobs also run
a headless browser flow against the packaged jar. Browser tooling stays in the
hosted runner's temporary directory and is not part of the app or release jar.
That packaged desktop-browser flow is a regression check; it does not replace
the real-iPad acceptance gate below.

## Candidate under test

- Commit SHA: pending
- Artifact: `target/jahaziel.jar`
- Artifact SHA-256: pending
- Workbench bundle size: recorded by CI
- Server target: Linux x64 / Linux arm64

## Packaged workflow checklist

1. Initialize a fresh data directory and start the packaged jar.
2. Confirm sign-in and authenticated workbench routes.
3. Apply each supported template family and verify separate workspace homes.
4. Open, edit, and save a text file; verify ETag conflicts after an external edit.
5. Explicitly enable an available language pack and verify a diagnostic arrives over its authenticated language-service stream.
6. Verify unauthenticated and cross-workspace file access is rejected.
7. Open a terminal; verify input, output, resize, reconnect buffer, close, and process cleanup.
8. Run and stop a template task; inspect bounded output and exit status.
9. Open a registered preview port, verify the workbench changes its status as
   the listener starts and stops, and verify workspace-scoped authorization.
10. In an existing repository, inspect status and diff, stage and unstage files, and commit staged changes.
11. Restart the jar and confirm file content and workspace identity recover.
12. Interrupt the server while a workspace is starting or active; confirm it reports a recoverable failure, preserves files, and starts successfully on retry.
13. Leave a run task with child processes active, abruptly stop the jar, and confirm startup recovery stops only the recorded Linux session, including its child processes. Confirm the workbench restores the latest run with its previous bounded output and interruption reason, and a stale or mismatched process marker never stops its PID.
14. Exercise path traversal, symlink escape, cross-workspace Git/file access, WebSocket authorization, and secret-redaction checks.
15. Confirm normal build does not download IDE servers or language packs and the jar contains no toolchains.
16. Review `doctor` data usage and Git availability; run cache prune in dry-run mode and confirm no files changed.
17. Back up and restore `data/` and `.env` according to `docs/operations/backup.md`.

## iPad release gate

Complete this matrix on real iPads for the current and two previous major
iPadOS versions before declaring the workbench shippable. Record the device
model, iPadOS version, browser version, and result for each run.

| Browser | Touch-only | Keyboard and trackpad | Portrait / landscape | Split View / Stage Manager |
|---|---|---|---|---|
| Safari | pending | pending | pending | pending |
| Chrome | pending | pending | pending | pending |
| Edge | pending | pending | pending | pending |

For every row, verify editing and touch selection, virtual keyboard behavior,
terminal shortcut keys and TUI interaction, preview opening in-panel and in a
new tab, tab suspension and draft recovery, WebSocket reconnect, and layout at
compact widths. Repeat the core flows with the external keyboard disconnected
so every control remains reachable by touch.

The optional catalog includes an opt-in Go 1.27.3 SDK and gopls v0.24.0 build
for Linux x64 and arm64. Its displayed download size covers the Go SDK only;
verified Go module downloads and temporary build space are additional. First
release scope provides clangd diagnostics on Linux x64; arm64 acceptance checks
an available cross-architecture language pack. C/C++ diagnostics on arm64 are
deferred until a small app-managed artifact can be pinned without depending on
a distro-specific package or downloading the full LLVM distribution.

## Results

| Date | Commit SHA | Artifact SHA-256 prefix | Native host | Result | Signed off by |
|---|---|---|---|---|---|
| — | — | — | Linux x64 | pending CI and human review | — |
| — | — | — | Linux arm64 | native CI and acceptance pending | — |
