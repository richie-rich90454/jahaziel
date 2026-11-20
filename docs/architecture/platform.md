<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platform

Last verified against version 0.1.0-SNAPSHOT.

`dev.jahaziel.platform` owns OS and architecture detection (`Platforms`,
`Platform`, `PosixPlatform`, `WindowsPlatform`) and nothing else does. The same
jar runs on Linux, macOS, and Windows; Ubuntu 26.04 LTS on Linux x64 and arm64
is the CI-verified reference host. Real-host acceptance on macOS and Windows is
still open. Windows, macOS, and Linux desktop browsers are clients and may host
the server; iPad is the primary browser client.

The iPad workbench must remain usable at compact widths with touch-only input
and with an external keyboard and trackpad. Device acceptance covers Safari,
Chrome, and Edge across the current and two previous major iPadOS versions.
Desktop CI alone does not qualify a release; see the
[platform guide](../guide/platforms.md).

## Per-OS strategies as plugins

Host behavior is contributed through the [plugin kernel](overview.md#plugin-kernel),
not through OS branches in feature code:

| Host | Process environment | Port discovery | Service supervisor | Caddy template |
|---|---|---|---|---|
| Linux x64 / arm64 | `LinuxProcessEnvironment` — `/bin/bash`, `HOME`/POSIX `PATH`, `setsid` process sessions, `/proc` for process roots | `LinuxPortDiscovery` — streaming reads of `/proc/net/tcp{6}` and `/proc/<pid>/fd` | `ops/services/jahaziel.service` (systemd) | `ops/caddy/Caddyfile.linux` |
| macOS | `MacProcessEnvironment` — `/bin/bash -i`, POSIX path; no Linux-only session or `/proc` support | `MacPortDiscovery` — `lsof -nP -iTCP -sTCP:LISTEN -Fpfn` | `ops/launchd/dev.jahaziel.plist.template` (launchd) | `ops/caddy/Caddyfile.macos` |
| Windows | `WindowsProcessEnvironment` — `cmd.exe`, `USERPROFILE`/`Path`, `ComSpec`; no POSIX session wrapper | `WindowsPortDiscovery` — `netstat -ano -p TCP` | `ops/windows/jahaziel-service.xml.template` (WinSW service wrapper) | `ops/caddy/Caddyfile.windows` |

`ProcessEnvironment` lives in `dev.jahaziel.platform.spi`; port discovery lives
in `dev.jahaziel.networking.internal`. `PluginWiringConfig` registers all six
concrete strategies, and each strategy reports whether it is supported on the
current host. `LinuxProcessEnvironment` requires the `setsid` utility at
runtime: workspace tasks and terminals are started in dedicated sessions so
startup recovery can stop the whole process tree without targeting a recycled
PID.

## Templates and tool catalogs

Server-managed starter templates under `src/main/resources/templates` carry a
per-OS command selector. A task or setup step declares its default `command`
plus `os.windows` and `os.darwin` overrides for command vectors and debug
settings; the server resolves the selector for the current host and the browser
never supplies executable paths. See the [template guide](../guide/templates.md).

Language packs and the OpenCode CLI use pinned, server-owned catalogs. Each
artifact entry records its OS, architecture, version, URL, and digest
(SHA-256 or SHA-512); downloads are verified against that digest before use
and stored in a shared immutable cache under the data directory. Nothing is
fetched at startup or during template application — a pack or tool is installed
only after explicit selection. Catalog additions require administrator-approved
metadata; users cannot provide arbitrary installer URLs.

## Service supervision and TLS

All hosts use the same jar-adjacent layout: `.env`, `data/`, and any configured
temp directory beside the jar, with only the service definition and Caddy
config kept where the OS requires them. Each `Caddyfile.<os>` template
reverse-proxies the loopback jar port (`11451`) with HSTS and security headers,
terminates TLS with automatic certificates for the exact IDE and preview
hostnames, and relays the terminal, language-service, debugger, and preview
WebSockets. No public route to port `11451` is added. Full install steps are in
the [deployment guide](../deployment/single-server.md#service-templates-per-os).

Supported server targets and the native x64/arm64 CI matrix are listed in
`docs/plan.md`.
