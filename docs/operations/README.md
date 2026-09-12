<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Operations

Day-to-day operation of a single-server deployment.

- Health: `systemctl status jahaziel-*`, `/actuator/health`, gateway
  `/admin/v1/health`, Prometheus alerts in the runbook below.
- Capacity: 30 concurrent VMs on the reference host; admission control
  refuses starts past the memory budget, never killing running VMs.
- Snapshots: daily at 02:00, retention at 03:30, backups offsite by 04:00.
- Upgrades: control plane blue/green, node agent rolling, database
  forward-only via Flyway, base images canary-first.
- Restore drills: quarterly, first Saturday, results in
  `restore-drill-log.md`.
