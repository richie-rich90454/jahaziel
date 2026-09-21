<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Backup

Back up the `./data/` folder beside the jar plus the `.env` file.
That is the whole install: `jahaziel.db` (SQLite), `workspaces/`,
`images/`, `jail/`, `audit/`, `sync/`, `keys/`, `logs/`.

1. Stop the jar.
2. Copy `./data/` and `.env` to backup storage.
3. Start the jar and confirm `http://localhost:11451/actuator/health`
   returns 200.

Restore is the reverse: put `./data/` and `.env` back beside the jar
and start it. Record every drill in
`docs/operations/restore-drill-log.md`.
