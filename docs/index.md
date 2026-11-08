<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel

A lightweight, self-hosted browser IDE for trusted users working with code on Linux servers.

Jahaziel keeps source files on the server and serves an editor, authenticated terminal, run tasks, and workspace previews from one Java application. The browser workbench uses CodeMirror 6 and xterm.js. The server targets Linux x64 and arm64; Windows, macOS, and Linux computers connect through a browser.

## Start here

- [Deploy on a Linux server](./deployment/single-server.md) for the complete jar, identity-provider, systemd, HTTPS, backup, and upgrade walkthrough.
- [Try the quick start](./guide/quick-start.md) with an existing jar or local demo.
- [Use the workbench](./guide/using-jahaziel.md) to create a workspace, edit and save files, run commands, and preview a project.
- [Review the current product plan](./plan.md) and [release acceptance checklist](./operations/acceptance.md).

## What it includes

- Separate persistent home directories for workspaces.
- Browser-based source editing with save-conflict detection.
- Explicit terminal sessions, template tasks, and workspace-scoped previews.
- Git status, diffs, staging, and commits for existing repositories.
- Pinned language tools that administrators or workspace owners opt into.
- Operations for health checks, logs, diagnostics, backup, and restore.

## Trust boundary

Jahaziel is intended for trusted users. Native terminal and task processes run as the same service account and are not isolated from one another at the operating-system level. Read the [threat model](./security/threat-model.md) before exposing a server.

Jahaziel does not bundle a VS Code server or project runtimes. Language packs are downloaded only when selected. See the [architecture overview](./architecture/overview.md) for implementation boundaries and the [platform guide](./guide/platforms.md) for supported hosts.
