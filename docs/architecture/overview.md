<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Architecture overview

Last verified against version 0.1.0-SNAPSHOT.

Jahaziel is one executable Spring Boot jar and a modular monolith for trusted
users. The same jar runs on Linux (x64 and arm64), macOS, and Windows. It
exposes one service port (`11451` by default) for the workbench, APIs,
authenticated WebSockets, and the workspace preview gateway; see
[ports](../reference/ports.md). All application-owned state is **jar-adjacent**:
`.env` and `data/` beside the jar, including the SQLite database, workspace
homes, keys, language packs, snapshots, outbox, and logs. Linux x64 and arm64
are the CI-verified targets; macOS and Windows have supervisor templates but
open real-host acceptance. iPad Safari, Chrome, and Edge are the primary
browser clients.

## Workbench

`web/src` is the only maintained UI source: SolidJS and DaisyUI, built by Vite
into same-origin static assets packaged under `static/`. CodeMirror 6 provides
text editing; xterm.js renders explicitly requested server-side PTYs. The jar
contains no VS Code fork, IDE server, or language-tool binaries. TypeScript is
formatted with `oxfmt`. The user-facing contract is in the
[workbench guide](../guide/using-jahaziel.md).

## Plugin kernel

Extension points are **abstract classes**, not interfaces or records. This
keeps new and touched Java at AP Computer Science A level while the build still
uses JDK 25. `dev.jahaziel.plugin` is the kernel:

- `Plugin` — abstract base with an immutable `PluginDescriptor` and
  `initialize`/`shutdown` lifecycle methods.
- `PluginDescriptor` — name, version, required plugin names, and capability
  strings; dependencies and capabilities are plain lists, not enums.
- `PluginRegistry` — registers plugins, resolves `requires` edges in dependency
  order, initializes in that order, and shuts down in reverse. Cycles and
  missing dependencies fail fast.
- `PluginLoader` — takes the built-in plugins and can load additional
  `*.jar` files from a jar-adjacent directory. Each jar must have a sibling
  `.sha256` file with its SHA-256 digest; a missing or mismatched checksum
  rejects the jar.
- `PluginContext` — the data directory and startup settings handed to every
  plugin.

Authentication providers are plugins: `AuthProviderPlugin extends Plugin` in
`dev.jahaziel.identity.auth`. `BasisAuthPlugin`, generic OIDC, Microsoft,
Google, GitHub, local password with TOTP, passkey, API token, and LDAP are
built-in providers. `PluginWiringConfig` registers them in priority order,
`AuthProviderGate` skips providers whose settings are absent, and
`AuthPluginRegistry` drives their life cycle. Capabilities such as
`MFA_ASSURANCE`, `REFRESH`, and `BACKCHANNEL` are capability strings on the
descriptor. There is no user-supplied plugin URL or extension marketplace.

Per-host behavior is also plugin-driven: `ProcessEnvironment` covers shells,
paths, environment variables, and process sessions for each OS, and
`PortDiscoveryPlugin` maps a tracked process to its listening ports. Neither
concern appears as an OS branch in feature code; see [platform](platform.md).

## Module map

The flat `src/main/java` tree keeps `dev.jahaziel.*` modular-monolith seams.
Feature packages expose a small public API and keep their implementation in
`internal` packages — deep modules, not shared internals:

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
- `dev.jahaziel.networking` and `dev.jahaziel.gateway` manage preview ports,
  port candidates from tracked process trees, and workspace-scoped routing.
  Port discovery plugins live in `dev.jahaziel.networking.internal`.
- `dev.jahaziel.storage` and `dev.jahaziel.snapshot` manage persistent data
  and snapshots under the configured data directory.
- `dev.jahaziel.dist` composes the single-server runtime; `dev.jahaziel.cli`,
  `dev.jahaziel.administration`, and `dev.jahaziel.monitoring` provide
  operations, diagnostics, and health reporting.
- `dev.jahaziel.plugin`, `dev.jahaziel.identity.auth`, and
  `dev.jahaziel.platform.spi` are the plugin seams described above.
- `dev.jahaziel.collaboration`, `dev.jahaziel.club`, `dev.jahaziel.datatools`,
  `dev.jahaziel.agent`, and `dev.jahaziel.agenttools` provide domain and REST
  services; `dev.jahaziel.ops`, `dev.jahaziel.audit`, `dev.jahaziel.policy`,
  and `dev.jahaziel.administration` cover operations and compliance. Unlike the
  earlier backend-only stage, these modules now have workbench service panels;
  see [workspaces](workspaces.md).

ArchUnit checks enforce package boundaries: feature packages use another
package's public API rather than its internals. The `dev.jahaziel.config`
composition root is the explicit exception — it may wire internal beans into
the application runtime, but feature packages do not depend on other
features' internal APIs. The former `dev.jahaziel.ide` integration is retired
from normal startup and jar packaging; see the
[retired IDE server note](ide.md) and the [current plan](../plan.md).

Further reading: [platform support](platform.md),
[workspace lifecycle](workspaces.md), and the
[security threat model](../security/threat-model.md).
