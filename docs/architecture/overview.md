<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Architecture overview

Last verified against version 0.1.0-SNAPSHOT.

Jahaziel is a single-jar, browser-based source IDE for trusted users working on
Linux server workspaces. The Linux x64 and arm64 server targets use one
application port and keep persistent files beneath the configured data
directory. Windows and macOS computers are browser clients; development on the
Windows workstation uses the repository's native PowerShell workflow.

The browser workbench is maintained in `web/src`, built into same-origin
static assets, and packaged into the jar. CodeMirror 6 provides text editing;
xterm.js displays explicitly requested workspace terminal sessions. The jar
does not contain the VS Code fork, an IDE server, or language-tool binaries.

The flat `src/main/java` tree keeps the `dev.jahaziel.*` modular-monolith
boundaries:

- `dev.jahaziel.api` exposes authenticated HTTP controllers and DTOs.
- `dev.jahaziel.identity` and `dev.jahaziel.authorization` handle sign-in,
  sessions, and workspace access decisions.
- `dev.jahaziel.workspace` and `dev.jahaziel.runtime` manage one persistent
  home per workspace, provisioning, tasks, and recovery.
- `dev.jahaziel.nodeagent`, `dev.jahaziel.spi`, and `dev.jahaziel.platform`
  provide the runtime backend seam. The release default runs requested
  processes under the Jahaziel service account; it does not claim hostile
  multi-tenant isolation.
- `dev.jahaziel.terminal` manages PTYs and authenticated terminal streams.
- `dev.jahaziel.language` manages explicitly selected, pinned language packs
  and their authenticated language-service streams.
- `dev.jahaziel.networking` and `dev.jahaziel.gateway` manage preview ports
  and workspace-scoped routing.
- `dev.jahaziel.storage` and `dev.jahaziel.snapshot` manage persistent data
  and snapshots under the configured data directory.
- `dev.jahaziel.dist` composes the single-server runtime; `dev.jahaziel.cli`,
  `dev.jahaziel.administration`, and `dev.jahaziel.monitoring` provide
  operations, diagnostics, and health reporting.

ArchUnit checks enforce package boundaries. A package uses another package's
public API rather than its internals. The former `dev.jahaziel.ide` integration
is retired from normal startup and jar packaging; see the
[workbench architecture](ide.md) and [current plan](../plan.md).

Further reading: [platform support](platform.md),
[workspace lifecycle](workspaces.md), and the
[security threat model](../security/threat-model.md).
