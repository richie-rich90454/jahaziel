<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# CLI reference

Last verified against version 0.1.0-SNAPSHOT.

Every command is invoked the same way on every OS from the directory that holds
`jahaziel.jar`:

| OS | Example |
|---|---|
| Windows (PowerShell) | `java -jar .\jahaziel.jar doctor` |
| macOS / Linux (shell) | `java -jar ./jahaziel.jar doctor` |

Quote paths that contain spaces. The commands below run offline: they do not
start the server. The jar resolves its own directory, so `.env`, `data/` and
backup bundles stay next to the jar even when launched from another folder.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success; also `--help`, `--version`, `update --status`, `upgrade --check` |
| `1` | Operation failed (missing file, unwritable path, failed check, tar missing) |
| `2` | Usage error (unknown command, missing or invalid argument) |

An unknown leading word prints usage and returns `2`. `doctor` returns `0` only
when every check passes, otherwise `1`.

## init

```bash
java -jar jahaziel.jar init
```

Writes a starter `.env` beside the jar (basis-auth placeholders plus a random
client secret) and creates `data/tmp/`. Refuses to overwrite an existing
`.env` and returns `1`. Fill in the credentials, run `doctor`, then boot.
See [environment variables](./env-vars.md) for the current key spellings.

## doctor

```bash
java -jar jahaziel.jar doctor
```

Runs extended pre-boot checks, one line each, plus a summary. A failed check
prints a `fix:` line. The base checks cover JDK 25+, a writable jar directory,
a well-formed `.env`, a free API port, SQLite write access, host/backend,
selected language tools, data storage sizes, a writable `data/tmp`, no other
instance holding the data lock, and the Git CLI.

Extended checks add: runtime architecture, update channel (`stable` pinned or
`latest`), pinned catalog checksums, IdP settings, data-directory writability,
the required `data/` subdirectories, at least 256 MB of disk headroom, and
capability availability. Doctor never downloads language packs or prunes
caches. With `--offline`, the capabilities check reports downloads unavailable.

## Run / server (no subcommand)

There is no `start` or `server` verb. Boot the server by invoking the jar with
no command (or with run flags only):

```bash
java -jar jahaziel.jar                 # boots API + node agent + gateway on 11451
java -jar jahaziel.jar --port=11452 --data-dir=/srv/jahaziel/data
```

With a jar-adjacent `.env` this boots the full stack on one port; without one
it prints help and exits `0`. A validated flag-only run keeps booting Spring.

| Flag | Effect |
|---|---|
| `--demo` | Stub IdP, seed demo user/workspace, ignore basis-auth credentials (see below) |
| `--offline` | Forbid tool downloads; report capabilities unavailable |
| `--port=N` | API port (default `11451`, or `PORT` from `.env`) |
| `--gateway-port=N` | Legacy standalone gateway socket (default `8081`; off unless enabled) |
| `--domain=HOST` | Public domain and token issuer (default `localhost`) |
| `--data-dir=PATH` | Data root (default `data/` next to the jar) |
| `--help`, `-h` | Print usage and exit `0` |
| `--version` | Print the version and exit `0` |

## demo

`demo` is a run flag, not a verb:

```bash
java -jar jahaziel.jar --demo
```

It binds the API port, uses a stub identity provider, seeds a demo user and
workspace, and ignores the basis-auth `BASIS_AUTH_*` / legacy `BASIS_*`
credentials. Do not use it for a production install.

## backup

```bash
java -jar jahaziel.jar backup [--no-secrets]
```

Writes `jahaziel-backup-<stamp>.tar.gz` beside the jar containing `.env` and
`data/`. `--no-secrets` excludes `.env` and `data/keys/jwt.key`. Prints the
archive path and SHA-256. Requires `tar` on PATH; returns `1` if it fails.

## restore

```bash
java -jar jahaziel.jar restore <backup.tar.gz>
```

Restores a backup into the jar directory. Verifies the gzip header, lists the
archive, refuses absolute or `..` paths and entries outside `.env`/`data/`,
then extracts. Returns `2` without a file, `1` for a bad or unsafe archive.
Take a fresh backup first; see [restore drills](../operations/backup.md).

## support-bundle

```bash
java -jar jahaziel.jar support-bundle
```

Writes `jahaziel-support-<stamp>.zip` beside the jar with redacted `.env`,
a doctor report, version/host details, and log files under 50 MB. Prints the
path and SHA-256. Attach it to a bug report; see
[bug report collection](../operations/update-and-rollback.md#bug-report-collection).

## update

```bash
java -jar jahaziel.jar update --status
java -jar jahaziel.jar update --to <jar> [--dry-run] [--sums <file>] [--version <v>]
java -jar jahaziel.jar update --rollback
```

Stages and verifies a candidate jar under `<data>/update/`. `--sums` checks
the SHA-256 manifest; `--version` labels the staged version. `--dry-run`
validates and reports without creating or replacing files. `--status` (also a
bare `update`) lists staged and applied updates. `--rollback` restores the jar
kept from the last applied update. Returns `2` when `--to` is missing, `1` on
checksum or staging failure. Updates apply on the next start; full steps are in
[update and rollback](../operations/update-and-rollback.md).

## upgrade

```bash
java -jar jahaziel.jar upgrade --check
java -jar jahaziel.jar upgrade --to <jar>
```

Offline jar replacement. `--check` (also a bare `upgrade`) prints the running
version. `--to` copies the current jar to `jahaziel.jar.bak-<stamp>` and
replaces it with the candidate; a locked jar fails with `1` and asks you to
stop the server first. See [update and rollback](../operations/update-and-rollback.md#safe-upgrade-procedure).

## capabilities

```bash
java -jar jahaziel.jar capabilities
```

Lists `opencode`, `ripgrep`, `language-packs`, and `updates` as available, or
as unavailable under `--offline`. This is an alias for `ops capabilities`.

## completion

```bash
java -jar jahaziel.jar completion bash   # or zsh, powershell
```

Prints a shell completion script. Returns `2` without a supported shell name.

## logs

```bash
java -jar jahaziel.jar logs [--follow|-f] [--level=LEVEL]
```

Tails `data/logs/control-plane.log` (last 100 lines). `--follow` streams new
lines; `--level` filters to `TRACE`, `DEBUG`, `INFO`, `WARN`, or `ERROR`.
`NO_COLOR` disables ANSI colors. Returns `1` when no log file exists, `2` for
an unknown level.

## ops

```bash
java -jar jahaziel.jar ops SUBCOMMAND [options]
```

Delegates to the operational and data-tool services without starting the
server:

| Subcommand | Purpose |
|---|---|
| `support-bundle` | Create a verified, redacted support bundle |
| `backup [--source=PATH]` | Snapshot a directory (default `data/workspaces`) with RPO/RTO summary |
| `restore-drill` | Restore the latest snapshot into scratch space and report timing |
| `audit query [--actor=X] [--action=Y] [--format=csv\|json]` | Query the audit log and verify its hash chain |
| `export-user <id>` | Produce a GDPR export archive for a user |
| `delete-user <id> --confirm 'DELETE <id>'` | Pseudonymize a user; requires typed confirmation |
| `reindex [--workspace=<uuid>]` | Rebuild the search index (all workspaces or one) |
| `capabilities` | List tool capabilities (same as the top-level verb) |

An unknown subcommand or missing argument returns `2`; a failed operation
returns `1`.

## Configuration

There is no `config` verb. Configuration is read from the jar-adjacent `.env`
and the real environment at startup; changing credentials requires a restart.
See [environment variables](./env-vars.md) and the
[configuration guide](../guide/configuration.md).

Failures at the API boundary carry a machine-readable code and a correlation
id; see [error codes](./errors.md).
