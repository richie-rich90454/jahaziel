<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Architecture overview

Last verified against version 0.1.0-SNAPSHOT.

Jahaziel is a single-jar, browser-based source IDE for trusted users. The same
jar runs on Linux, macOS, and Windows with one application port and persistent
files beneath the jar-adjacent data directory. Linux x64 and arm64 are the
CI-verified targets; real-host acceptance on macOS and Windows is still open.
iPad is the primary browser client, and development on the Windows workstation
uses the repository's native PowerShell workflow.

The browser workbench is maintained in `web/src` with SolidJS and DaisyUI, built
into same-origin static assets, and packaged into the jar. CodeMirror 6 provides
text editing; xterm.js displays explicitly requested workspace terminal
sessions. The jar does not contain the VS Code fork, an IDE server, or
language-tool binaries. TypeScript is formatted with `oxfmt`.

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
- `dev.jahaziel.debug` manages workspace-authorized Debug Adapter Protocol
  sessions. The server launches only administrator-configured adapters and
  exposes each active session through a workspace- and session-bound
  WebSocket. Launch configurations are workspace-rooted, and attach requests
  resolve only opaque targets issued for active server-declared workspace
  tasks; adapter installation remains administrator-managed.
- `dev.jahaziel.language` manages explicitly selected, pinned language packs
  and their authenticated language-service streams.
- `dev.jahaziel.networking` and `dev.jahaziel.gateway` manage preview ports
  and workspace-scoped routing.
- `dev.jahaziel.storage` and `dev.jahaziel.snapshot` manage persistent data
  and snapshots under the configured data directory.
- `dev.jahaziel.dist` composes the single-server runtime; `dev.jahaziel.cli`,
  `dev.jahaziel.administration`, and `dev.jahaziel.monitoring` provide
  operations, diagnostics, and health reporting.
- `dev.jahaziel.plugin`, `dev.jahaziel.identity.auth`, and
  `dev.jahaziel.platform.spi` are the plugin seams: extension lifecycle,
  authentication providers, and per-OS process environments. Port discovery
  plugins live in `dev.jahaziel.networking.internal`.
- `dev.jahaziel.collaboration`, `dev.jahaziel.agent`, `dev.jahaziel.agenttools`,
  `dev.jahaziel.datatools`, and `dev.jahaziel.club` deliver backend domain and
  service modules; `dev.jahaziel.audit`, `dev.jahaziel.policy`, and
  `dev.jahaziel.administration` cover compliance. These modules are backend-only
  at this stage: there is no dedicated workbench panel for them yet.

ArchUnit checks enforce package boundaries. Feature packages use another
package's public API rather than its internals. The `dev.jahaziel.config`
composition root is the explicit exception: it may wire internal beans into
the application runtime, but feature packages do not depend on other
features' internal APIs. The former `dev.jahaziel.ide` integration is retired
from normal startup and jar packaging; see the
[workbench architecture](ide.md) and [current plan](../plan.md).

Further reading: [platform support](platform.md),
[workspace lifecycle](workspaces.md), and the
[security threat model](../security/threat-model.md).
