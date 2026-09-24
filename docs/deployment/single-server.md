<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Single-server deployment

Last verified against version 0.1.0-SNAPSHOT.

One jar plus one `.env` file, serving port 11451.

## 1. Install

Build once with Java 25:

```bash
./mvnw -q -DskipTests package   # produces target/jahaziel.jar
```

Copy `target/jahaziel.jar` into an empty folder on the machine, then:

```bash
java -jar jahaziel.jar init   # scaffolds .env beside the jar
```

Fill in `.env` with a text editor. Login stays off until all required
values are present.

## 2. Run

```bash
java -jar jahaziel.jar   # serves http://localhost:11451
```

Only Java 25 is required on the machine. First run creates `./data/`
beside the jar.

## 3. Data layout

Everything the runtime owns lives under `./data/`:

| Path | Contents |
|---|---|
| `data/workspaces/` | workspace homes |
| `data/images/` | base images |
| `data/jail/` | per-workspace sandboxes |
| `data/audit/` | audit staging |
| `data/sync/` | file-sync state |
| `data/keys/` | server keys |
| `data/logs/` | server logs |
| `data/plugins/` | installed plugins |
| `data/templates/` | seeded workspace templates |
| `data/ide/` | REH tarball and extracted IDE server |
| `data/tmp/` | the JVM temp dir the server forces at launch |
| `data/backups/` | scheduled backups |
| `data/dumps/` | diagnostics dumps |
| `data/.lock` | instance lock; held while the server runs |
| `data/.initialized` | first-boot marker |
| `data/jahaziel.db` | SQLite file, created once, never cleared |

`doctor` reports `tmpdir` as `data/tmp` because the launch path sets
`java.io.tmpdir` there before Spring starts; nothing else is required.

Back up `./data/` plus `.env`; that is the whole install. See
`docs/operations/backup.md`.

## 4. Verify

Follow `docs/operations/verification.md`, then record release
acceptance in `docs/operations/acceptance.md`.
