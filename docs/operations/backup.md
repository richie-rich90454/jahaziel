<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Backup

Everything the app owns lives beside the jar: `.env` plus `data/`
(`jahaziel.db`, `workspaces/`, `images/`, `jail/`, `audit/`, `sync/`,
`keys/`, `logs/`). The CLI has two backup paths: a full-install archive and
a workspace snapshot. Stop the jar for a consistent full backup.

## Full install: `backup` and `restore`

~~~bash
# archive beside the jar: .env + data/, SHA-256 printed
java -jar jahaziel.jar backup
# omit .env and data/keys/jwt.key when the archive leaves trusted storage:
java -jar jahaziel.jar backup --no-secrets
~~~

This writes `jahaziel-backup-<stamp>.tar.gz` beside the jar and requires
`tar` on `PATH`. Treat the archive as secret material and copy it off host.

To restore, stop the service first, move the current `.env` and `data/`
into a uniquely named hold directory, then extract the archive:

~~~bash
java -jar jahaziel.jar restore /path/to/jahaziel-backup-<stamp>.tar.gz
~~~

`restore` extracts over the install directory and does not remove files
that are absent from the archive, so the hold step keeps the restore
clean. See the complete stop/hold/restore/start sequence in
[single-server.md §9](../deployment/single-server.md#9-back-up-and-restore).

Manual alternative: stop the jar, copy `./data/` and `.env` to backup
storage, start the jar, and confirm `/actuator/health` returns 200.
Restore is the reverse. Record every drill in the
[restore drill log](./restore-drill-log.md).

## Workspace snapshots: `ops backup`

~~~bash
java -jar jahaziel.jar ops backup [--source=<dir>]
~~~

Compresses `data/workspaces/` (or `--source`) into
`data/backups/backup-<stamp>.zip` with a SHA-256 manifest. RPO is 24
hours and RTO is 2 hours.

## Restore drill: `ops restore-drill`

~~~bash
java -jar jahaziel.jar ops restore-drill
~~~

Restores the newest snapshot into a disposable workspace, verifies the
manifest, and reports files, elapsed time, and `withinRto`. Run it
quarterly (first Saturday) and record the result in the
[restore drill log](./restore-drill-log.md).

Limitation: the drill restores into a fresh scratch data directory, not in
place. On Windows, read-only snapshots make in-place `tar` extraction
fail, so a real-host in-place restore with snapshots is only exercised by
the live-host gate in the
[release acceptance record](./release-acceptance.md).
