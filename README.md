<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel

Self-hosted browser-based development platform: every authorized
developer gets their own Linux development machine, accessible entirely
through a browser. One login, one browser tab: a real Linux machine, a
real IDE, a real terminal.

One jar, one port, one data folder. See `docs/plan.md` for the
shipping plan.

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
./mvnw -q -DskipTests package   # produces target/jahaziel.jar
```

Requires JDK 25 and Node 24.18. Full checks: `./mvnw verify`.
Smoke profile: `./mvnw verify -Psmoke`. See `CONTRIBUTING.md`.

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

| OS | Arch | REH | Backend | Jar mode |
|---|---|---|---|---|
| Linux | x86_64 | fat | Firecracker (KVM), local fallback | single jar |
| Linux | arm64 | fat | Firecracker (KVM), local fallback | single jar |
| Windows | x86_64 | slim | local | single jar |
| Windows | arm64 | slim | local | single jar |
| macOS | x86_64 | download | local | single jar |
| macOS | arm64 | download | local | single jar |

Port 11451 on all six. The local backend runs workspaces as host
processes with no isolation boundary; see
`docs/security/threat-model.md`.

## Docs

- Plan: `docs/plan.md`
- Deploy: `docs/deployment/single-server.md`
- Operate: `docs/operations/` (backup, reset, verification, acceptance)
- CLI: `docs/reference/cli.md`
- Contribute: `CONTRIBUTING.md`

## License

Apache License 2.0. See `LICENSE`.
