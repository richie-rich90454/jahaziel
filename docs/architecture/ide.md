<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Retired IDE server architecture

> **Status: retired. Do not use this page as the current architecture.**
> The VS Code OSS fork, the `vscode-reh-web-*` server, the REH extraction step,
> per-workspace IDE processes, and the `dev.jahaziel.ide` integration were
> removed from normal startup and jar packaging. The decision is recorded as
> [superseded design history](../contributing/architecture-decisions/ide-server-base.md).
> The older build notes under `docs/ide/` are historical artifacts, not setup
> instructions.

Jahaziel now serves one lightweight, same-origin browser workbench from its
single jar. `web/src` is the maintained TypeScript source (SolidJS, DaisyUI,
CodeMirror 6, xterm.js); Vite builds it into static assets that Maven packages
under `static/`. No editor server, REH binary, language server, compiler, or
toolchain is bundled in the jar.

Maintained documentation:

- [Architecture overview](overview.md) — jar layout, plugin kernel, module seams.
- [Workspace architecture](workspaces.md) — files, tasks, terminal, preview,
  snapshots, and the wired service panels.
- [Platform architecture](platform.md) — per-OS process and port strategies.
- [Using the workbench](../guide/using-jahaziel.md) — user-facing editor,
  OpenCode, language diagnostics, and debugger workflow.
- [Templates](../guide/templates.md) — server-managed starters and run tasks.
- [Product plan](../plan.md) — current contract and what remains deferred.

The first release deliberately excludes VS Code extension compatibility, a
plugin marketplace, full VS Code debugger parity, and real-time collaborative
editing. Language packs are pinned and downloaded only after explicit
selection; debug adapters are administrator-configured, and attach targets are
opaque IDs for live server-tracked workspace tasks rather than raw process IDs.
Windows and macOS are browser clients and can host the jar, but only Linux x64
and arm64 are CI-verified server targets; see
[platforms](../guide/platforms.md).
