<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel

A lightweight, self-hosted browser IDE for trusted users working with code on their own server.

Jahaziel keeps source files on the server and serves an editor, authenticated terminal, run tasks, and workspace previews from one Java application. The browser workbench uses SolidJS, DaisyUI, CodeMirror 6, and xterm.js. One jar, one service port, and all state below the configured data directory. The same jar runs on Linux, macOS, and Windows; Linux x64 and arm64 are the CI-verified targets, and real-host acceptance on the other OSes is still open.

## Docs map

### Start here

- [Quick start](./guide/quick-start.md) — boot an existing jar or the local demo.
- [Use the workbench](./guide/using-jahaziel.md) — workspaces, files, tasks, terminal, previews.
- [Configuration](./guide/configuration.md) — credentials, providers, secret rotation.
- [basis-auth integration](./basis_auth_setup_guide.md) — the downstream IdP contract: endpoints, claims, backchannel, registration, error codes.
- [Platforms](./guide/platforms.md) — per-OS process and port behavior; [templates](./guide/templates.md), [demo mode](./guide/demo-mode.md), [share links](./guide/share-links.md).

### Deploy and operate

- [Deploy on a Linux server](./deployment/single-server.md) — jar, identity provider, systemd, HTTPS, backup and upgrade, plus launchd and Windows service templates.
- [Update and rollback](./operations/update-and-rollback.md) — stage, apply, and revert a jar update.
- [Bugfix workflow](./operations/bugfix-workflow.md) — take a production bug through staging to production.
- [Operations runbooks](./operations/README.md) — health, backup, reset, verification, restore drills.
- [Reference](./reference/cli.md) — CLI, [environment variables](./reference/env-vars.md), [error codes](./reference/errors.md), [ports](./reference/ports.md).

### Plan and acceptance

- [Current product plan](./plan.md) — status, delivered work, and remaining external gates.
- [Release acceptance checklist](./operations/acceptance.md) and [release acceptance record](./operations/release-acceptance.md) — gate status and sign-off.
- [Historical plan](./plan-legacy.md) — **retired**; design history only.

## What it includes

- Separate persistent home directories for workspaces.
- Browser-based source editing with save-conflict detection.
- Explicit terminal sessions, template tasks, and workspace-scoped previews.
- Git status, diffs, staging, and commits for existing repositories.
- Pinned language tools that administrators or workspace owners opt into.
- Operations for health checks, logs, diagnostics, backup, and restore.

Additional delivered modules have authenticated workbench panels in `web/src`
alongside their domain/service layers: collaboration (`dev.jahaziel.collaboration`),
agent assistant (`dev.jahaziel.agent`) and agent tools (`dev.jahaziel.agenttools`), data tools
(`dev.jahaziel.datatools`), Club (`dev.jahaziel.club`), observability
(`dev.jahaziel.monitoring`), and compliance (`dev.jahaziel.audit`,
`dev.jahaziel.policy`, `dev.jahaziel.administration`). See
[Workspaces](architecture/workspaces.md) for the panel map.

## Trust boundary

Jahaziel is intended for trusted users. Native terminal and task processes run as the same service account and are not isolated from one another at the operating-system level. Read the [threat model](./security/threat-model.md) before exposing a server.

Jahaziel does not bundle a VS Code server or project runtimes. Language packs are downloaded only when selected. See the [architecture overview](./architecture/overview.md) for implementation boundaries and the [platform guide](./guide/platforms.md) for supported hosts.
