<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Acceptance (Phase 9)

Phase 9 is human-run on real hardware. A person works through the
checklist below for each of the six OS/arch combos in `docs/plan.md`,
then records the result with the release commit SHA. The release is
accepted only when every row passes. No automation stands in for it.

## Candidate under test

- Commit SHA: `dc4a8c0`
- Artifact: `target/jahaziel.jar`
  (`SHA-256 F69EECF267C4BEAE4BDF40082D9F3E6095416F9B7DF51246DC5BEE3FAABD5CFB`)
- Changes since the last acceptance run: first acceptance for the
  single-jar layout (one pom, flat `src/`, platform abstraction, bare-jar
  full-stack boot, CLI verbs, smoke gate, template seeds, VitePress site).
  No previous acceptance recorded.

## Checklist

1. `java -jar jahaziel.jar init` scaffolds `.env`.
2. Bare `java -jar jahaziel.jar` serves `http://localhost:11451`.
3. Health endpoint returns 200.
4. Login flow completes (or stays off with a clear message when
   `.env` is incomplete).
5. Workspace create/start/stop/destroy round-trips.
6. IDE opens over the correct REH mode for the combo.
7. Terminal runs a command; port forward serves.
8. Snapshot create/restore round-trips.
9. Backup and restore round-trip per `docs/operations/backup.md`.
10. Nothing written outside `./data/`.

## Results

| Date | Commit SHA | Artifact SHA-256 (prefix) | Combo | REH | Result | Signed off by |
|---|---|---|---|---|---|---|
| — | `dc4a8c0` | `F69EECF2…` | Windows x64 | win32-x64 | untested | — |
| — | `dc4a8c0` | `F69EECF2…` | macOS arm64 | darwin-arm64 | untested | — |
| — | `dc4a8c0` | `F69EECF2…` | Linux x64 | linux-x64 | untested | — |
| — | `dc4a8c0` | `F69EECF2…` | Windows arm64 | win32-arm64 | untested | — |
| — | `dc4a8c0` | `F69EECF2…` | macOS x64 | darwin-x64 | untested | — |
| — | `dc4a8c0` | `F69EECF2…` | Linux arm64 | linux-arm64 | untested | — |
