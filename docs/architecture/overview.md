<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Architecture overview

One jar (`target/jahaziel.jar`), one port (11451), one data folder
(`./data/`). The flat `src/main/java` tree is organized as
`dev.jahaziel.*` packages:

- `dev.jahaziel.api` — HTTP controllers and DTOs; the only HTTP
  surface besides the gateway loopback.
- `dev.jahaziel.workspace`, `dev.jahaziel.runtime` — workspace
  lifecycle and process/VM supervision.
- `dev.jahaziel.node`, `dev.jahaziel.nodeagent` — placement facade
  over the `dev.jahaziel.spi.NodeBackend` interface; the local
  process backend and the Firecracker backend both implement it.
- `dev.jahaziel.storage`, `dev.jahaziel.snapshot` — folder storage
  under `./data/` and copy-on-write workspace state.
- `dev.jahaziel.networking`, `dev.jahaziel.gateway` — per-workspace
  ports and Host-header routing with JWT verification; no
  control-plane round trip per request.
- `dev.jahaziel.terminal` — shell sessions bound to workspaces.
- `dev.jahaziel.identity`, `dev.jahaziel.authorization`,
  `dev.jahaziel.policy`, `dev.jahaziel.extension` — login
  (DevConnect/OIDC, off until `.env` is complete), access decisions,
  and the Open VSX-only extension policy.
- `dev.jahaziel.audit` — guest events over vsock to a collector that
  appends outside user reach; see `docs/security/threat-model.md`.
- `dev.jahaziel.monitoring`, `dev.jahaziel.administration` — health
  (`/actuator/health`) and admin APIs.
- `dev.jahaziel.platform` — the only package that branches on the OS.
- `dev.jahaziel.dist` — single-jar wiring: `.env` loading,
  jar-adjacent `./data/` layout, SQLite, loopback gateway.
- `dev.jahaziel.spi`, `dev.jahaziel.config` — plugin interfaces and
  shared configuration.
- `dev.jahaziel.ide` — Jahaziel Code integration; see
  `docs/architecture/ide.md`.

Package boundaries are enforced by ArchUnit: a package uses another
package's public API only, never its internals.

Further reading: `docs/architecture/platform.md`,
`docs/architecture/workspaces.md`, `docs/architecture/ide.md`,
`docs/plan.md`.
