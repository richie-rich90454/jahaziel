<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel

Self-hosted browser-based development environment for trusted users.
Jahaziel runs on a Linux x64 or arm64 server and provides a lightweight
workbench, workspace files, terminals, run tasks, and preview links to
browser clients on Windows, macOS, and Linux.

One jar, one port, one data folder. Workspace files are separated by
workspace. Native workspace processes share the Jahaziel service account;
the local backend is for trusted users, not hostile multi-tenant isolation.
See `docs/plan.md` for the current product contract and release plan.

## Quick Start

```bash
java -jar jahaziel.jar init   # scaffolds .env beside the jar
# fill in .env with a text editor
java -jar jahaziel.jar        # serves http://localhost:11451
```

First run creates `./data/` beside the jar (workspaces, images, jail,
audit, sync, keys, logs, and `jahaziel.db`, a SQLite file created once
and never cleared). Login stays off until `.env` holds all required
values.

## Build

```bash
./dev.ps1 build                 # builds the workbench, then target/jahaziel.jar
```

Requires existing JDK 25, Node 24+, and the pinned pnpm package manager.
Dependencies and caches stay in ignored repository-local directories.
Full checks: `./mvnw verify`; packaged smoke: `./dev.ps1 smoke`.
See `CONTRIBUTING.md`.

## Layout

Flat `src/main/java` under `dev.jahaziel.*` packages:

- `api` — HTTP controllers and DTOs
- `workspace`, `runtime`, `node`, `nodeagent` — lifecycle and backends
- `storage`, `snapshot`, `networking` — folders, COW state, ports
- `terminal`, `gateway` — shell sessions and host routing
- `identity`, `authorization`, `policy`, `extension` — login and rules
- `audit`, `monitoring`, `administration` — trail, health, admin
- `platform` — the only place that branches on the OS
- `dist` — single-jar wiring over jar-adjacent `./data/`
- `spi`, `config` — plugin interfaces and shared configuration

Details: `docs/architecture/overview.md`.

## Platforms

| Server OS | Arch | Workbench client |
|---|---|---|
| Linux | x86_64 | Any supported modern browser |
| Linux | arm64 | Any supported modern browser |

Port 11451 serves the UI, API, terminal WebSocket, and preview gateway.
Windows and macOS are browser clients; native Jahaziel servers on those
systems are deferred.

## Docs

- Plan: `docs/plan.md`
- Deploy: `docs/deployment/single-server.md`
- Operate: `docs/operations/` (backup, reset, verification, acceptance)
- CLI: `docs/reference/cli.md`
- Contribute: `CONTRIBUTING.md`

## License

Apache License 2.0. See `LICENSE`.
