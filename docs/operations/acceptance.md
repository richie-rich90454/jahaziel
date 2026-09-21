<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Acceptance (Phase 9)

Phase 9 is human-run on real hardware. A person works through the
checklist below for each of the six OS/arch combos in `docs/plan.md`,
then records the result with the release commit SHA. The release is
accepted only when every row passes. No automation stands in for it.

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

| Date | Commit SHA | Combo | REH | Result | Signed off by |
|---|---|---|---|---|---|
| — | `0000000` (placeholder: paste the release SHA) | — | — | No acceptance recorded yet. | — |
