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
with the service definition kept where the OS requires it. The data directory
holds the SQLite database (`data/jahaziel.db`), workspace homes, keys, logs,
language packs, and update/rollback state. See
[service templates per OS](../deployment/single-server.md#service-templates-per-os)
for the install steps, and [Data layout](../deployment/single-server.md#data-layout)
for the full tree.

Linux x64 and arm64 (Ubuntu 26.04 LTS is the reference) are the only hosts with
native CI verification. Real-host acceptance on macOS and Windows is still
open, so do not treat those templates as release-verified deployments. The
Windows Playwright E2E suite is a browser regression check, not host acceptance.

## Supervisor commands by OS

Register the jar with the host supervisor for restart-on-failure, boot-at-start,
and log capture. Complete the jar install and `init`/`doctor` first, as in
[Path 1](../deployment/single-server.md#path-1--packaged-jar-upload).

### Linux (systemd)

~~~bash
sudo install -o root -g root -m 0644 ops/services/jahaziel.service /etc/systemd/system/jahaziel.service
sudo systemctl daemon-reload
sudo systemctl enable --now jahaziel     # enable + start
sudo systemctl status jahaziel --no-pager
sudo journalctl -u jahaziel -f           # logs
sudo systemctl restart jahaziel
sudo systemctl stop jahaziel
~~~

The unit runs `doctor` via `ExecStartPre` and binds the jar to `127.0.0.1`.

### macOS (launchd)

~~~bash
sudo sh ops/launchd/install.sh /var/lib/jahaziel dev.jahaziel  # install + load
launchctl print system/dev.jahaziel                            # status
tail -f /var/lib/jahaziel/data/logs/launchd.out.log /var/lib/jahaziel/data/logs/launchd.err.log
launchctl kickstart -k system/dev.jahaziel                     # restart
launchctl bootout system/dev.jahaziel                          # stop
~~~

`install.sh` writes the plist to `/Library/LaunchDaemons` (outside the jar
directory), keeps stdout/stderr logs and `java.io.tmpdir` under `<jar>/data`,
and loads it with `RunAtLoad` and `KeepAlive`.

### Windows (WinSW)

~~~powershell
.\ops\windows\install.ps1 -JarDir C:\jahaziel -WinSw C:\tools\WinSW.exe
Get-Service -Name jahaziel                       # status
Get-Content C:\jahaziel\data\logs\*.log -Wait    # logs
Restart-Service -Name jahaziel
Stop-Service -Name jahaziel
Start-Service -Name jahaziel
~~~

The `jahaziel-service.xml` config stays jar-adjacent; the service itself is
registered with Windows Services. Stop the service before replacing the jar:
Windows locks a running jar file.

## Edge proxy per OS

The Caddyfiles in `ops/caddy/` terminate TLS and reverse-proxy the loopback
port with HSTS and security headers. Copy the matching template
(`Caddyfile.linux`, `Caddyfile.macos`, or `Caddyfile.windows`; the bare
`Caddyfile` is the Linux-compatible baseline) to that host's Caddy config,
replace the example hostnames, and forward WebSockets for terminal, preview,
and language-service traffic. See
[Path 2 — Caddy and HTTPS](../deployment/single-server.md#caddy-and-https).

## Updates on every OS

`java -jar jahaziel.jar update --to <jar> --sums SHA256SUMS` stages a verified
jar under `<jar>/data/update/<version>/`; the swap happens on the next start.
Stop the supervisor first (mandatory on Windows), then restart, run `doctor`,
and check `http://127.0.0.1:11451/actuator/health`. Roll back with
`java -jar jahaziel.jar update --rollback`. See
[Updating and rollback](../deployment/single-server.md#updating-and-rollback).

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
