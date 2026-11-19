<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platforms

Last verified against version 0.1.0-SNAPSHOT.

The same `jahaziel.jar` runs on Linux, macOS, and Windows for both development
and production. Host-specific behavior is isolated in plugins rather than
branches in feature code: `dev.jahaziel.platform.spi` supplies the process
environment (shell, path, and environment variables) for each OS, and
`dev.jahaziel.networking.internal` supplies per-OS port discovery.

| Host | Supervisor template | Process environment | Port discovery |
|---|---|---|---|
| Linux x64 / arm64 | `ops/services/jahaziel.service` (systemd) | `LinuxProcessEnvironment` | `LinuxPortDiscovery` |
| macOS | `ops/launchd/dev.jahaziel.plist.template` (launchd) | `MacProcessEnvironment` | `MacPortDiscovery` |
| Windows | `ops/windows/jahaziel-service.xml.template` (WinSW service wrapper) | `WindowsProcessEnvironment` | `WindowsPortDiscovery` |

All hosts share the same jar-adjacent layout: `.env` and `data/` beside the jar,
with the service definition kept where the OS requires it. See
[service templates per OS](../deployment/single-server.md#service-templates-per-os).

Linux x64 and arm64 (Ubuntu 26.04 LTS is the reference) are the only hosts with
native CI verification. Real-host acceptance on macOS and Windows is still
open, so do not treat those templates as release-verified deployments. The
Windows Playwright E2E suite is a browser regression check, not host acceptance.

iPadOS Safari, Chrome, and Edge are the primary browser clients, with touch-only
as well as keyboard and trackpad workflows. Windows, macOS, and Linux desktops
also connect through a browser and may host the server as well.

The release target is the current and two previous major iPadOS versions.
Release acceptance must cover each browser on a real iPad in portrait,
landscape, Split View, and Stage Manager, both touch-only and with an external
keyboard and trackpad. Desktop browser automation is useful for regression
checks but does not replace this device acceptance.

Port `11451` serves the UI, APIs, terminal WebSocket, and workspace preview
gateway. The local backend runs terminal and task processes under the same
OS account as Jahaziel. It is for trusted users only; see the
[threat model](../security/threat-model.md).
