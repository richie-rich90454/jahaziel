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
  04:00; see [Backup](./backup.md).
- Upgrades: stage with `update`, swap on next start; the schema in
  `./data/jahaziel.db` migrates forward on boot. See
  [Update and rollback](./update-and-rollback.md).
- Reset: [Reset](./reset.md).
- Verification: [Verification](./verification.md).
- Acceptance: [Release acceptance](./acceptance.md) and
  [release acceptance record](./release-acceptance.md).
- Restore drills: quarterly, first Saturday, results in the
  [restore drill log](./restore-drill-log.md).

## Runbooks

| Runbook | Use it when |
|---|---|
| [Update and rollback](./update-and-rollback.md) | Upgrading the jar, applying a staged update, or reverting a bad one |
| [Bugfix workflow](./bugfix-workflow.md) | Fixing a production bug and getting the fix to staging, then production |
| [Backup](./backup.md) | Taking a backup, restoring one, or running a restore drill |
| [Reset](./reset.md) | Returning an install to a clean state (destructive) |
| [Verification](./verification.md) | Checking a deploy or upgrade, including current test counts |
| [Release acceptance](./acceptance.md) | Running the packaged acceptance checklist and iPad matrix |
| [Release acceptance record](./release-acceptance.md) | Current gate status and sign-off |
| [Restore drill log](./restore-drill-log.md) | Recording quarterly restore drill results |
| [Deployment guide](../deployment/single-server.md) | Initial install, service templates, HTTPS, and DNS |

## When something goes wrong

Follow the first matching branch. Stop and escalate if a step cannot be
safely repeated.

- **Health check failing** (no 200 from `/actuator/health`): run
  `java -jar jahaziel.jar doctor` and read the log with
  `java -jar jahaziel.jar logs`. If it started after an upgrade, go to
  [Rollback](./update-and-rollback.md#rollback).
- **Login failing**: check the required `BASIS_AUTH_*` values and
  `CURRENT_URL_ORIGIN`; a credential change needs a restart. See the
  [deployment troubleshooting](../deployment/single-server.md#troubleshooting).
  Send the IdP administrator a redacted support bundle.
- **Task or preview failing**: confirm the project runtime is installed on
  the host and preview DNS/TLS is correct (same troubleshooting table);
  find the correlation id in the log and collect a support bundle.
- **Disk full**: run `doctor` for data/cache sizes, prune caches in dry-run
  mode first, then free space before any backup or upgrade. Never delete
  `./data/` to free space; take a backup first.
- **Bad update**: check `java -jar jahaziel.jar update --status`, stop the
  service, `update --rollback`, restart, then re-run
  [Verification](./verification.md). Full steps in
  [Rollback](./update-and-rollback.md#rollback).
