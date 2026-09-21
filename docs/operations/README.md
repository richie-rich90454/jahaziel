<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Operations

Day-to-day operation of the single jar (`java -jar jahaziel.jar`,
port 11451, everything under `./data/`).

- Health: `http://localhost:11451/actuator/health`.
- Capacity: admission control refuses starts past the memory budget,
  never killing running VMs.
- Snapshots: daily at 02:00, retention at 03:30, backups offsite by
  04:00; see `docs/operations/backup.md`.
- Upgrades: stop the jar, replace it, start it; the schema in
  `./data/jahaziel.db` migrates forward on boot.
- Reset: `docs/operations/reset.md`.
- Verification: `docs/operations/verification.md`.
- Acceptance: `docs/operations/acceptance.md`.
- Restore drills: quarterly, first Saturday, results in
  `restore-drill-log.md`.
