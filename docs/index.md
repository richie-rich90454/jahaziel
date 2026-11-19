<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel

A lightweight, self-hosted browser IDE for trusted users working with code on their own server.

Jahaziel keeps source files on the server and serves an editor, authenticated terminal, run tasks, and workspace previews from one Java application. The browser workbench uses SolidJS, DaisyUI, CodeMirror 6, and xterm.js. The same jar runs on Linux, macOS, and Windows; Linux x64 and arm64 are the CI-verified targets, and real-host acceptance on the other OSes is still open.

## Start here

- [Deploy on a Linux server](./deployment/single-server.md) for the complete jar, identity-provider, systemd, HTTPS, backup, and upgrade walkthrough, plus launchd and Windows service templates.
- [Try the quick start](./guide/quick-start.md) with an existing jar or local demo.
- [Use the workbench](./guide/using-jahaziel.md) to create a workspace, edit and save files, run commands, and preview a project.
- [Check platforms and supervisors](./guide/platforms.md) for per-OS process and port behavior.
- [Review the current product plan](./plan.md) and [release acceptance checklist](./operations/acceptance.md).

## What it includes

- Separate persistent home directories for workspaces.
- Browser-based source editing with save-conflict detection.
- Explicit terminal sessions, template tasks, and workspace-scoped previews.
- Git status, diffs, staging, and commits for existing repositories.
- Pinned language tools that administrators or workspace owners opt into.
- Operations for health checks, logs, diagnostics, backup, and restore.

Additional delivered modules are backend-only at this stage (domain/service
layer, no dedicated workbench panel): collaboration (`dev.jahaziel.collaboration`),
agent assistant (`dev.jahaziel.agent`) and agent tools (`dev.jahaziel.agenttools`), data tools
(`dev.jahaziel.datatools`), Club (`dev.jahaziel.club`), observability
(`dev.jahaziel.monitoring`), and compliance (`dev.jahaziel.audit`,
`dev.jahaziel.policy`, `dev.jahaziel.administration`).

## Trust boundary

Jahaziel is intended for trusted users. Native terminal and task processes run as the same service account and are not isolated from one another at the operating-system level. Read the [threat model](./security/threat-model.md) before exposing a server.

Jahaziel does not bundle a VS Code server or project runtimes. Language packs are downloaded only when selected. See the [architecture overview](./architecture/overview.md) for implementation boundaries and the [platform guide](./guide/platforms.md) for supported hosts.
