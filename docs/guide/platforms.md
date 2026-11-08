<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platforms

Last verified against version 0.1.0-SNAPSHOT.

Jahaziel servers run on Linux x64 and arm64. iPad is the primary client,
with touch-only as well as keyboard and trackpad workflows. Windows, macOS,
and Linux desktops also connect through a browser; they do not host a
Jahaziel server in this release.

| Server | Arch | Browser clients |
|---|---|---|
| Linux | x64 | iPadOS Safari, Chrome, Edge; desktop browsers |
| Linux | arm64 | iPadOS Safari, Chrome, Edge; desktop browsers |

The release target is the current and two previous major iPadOS versions.
Release acceptance must cover each browser on a real iPad in portrait,
landscape, Split View, and Stage Manager, both touch-only and with an external
keyboard and trackpad. Desktop browser automation is useful for regression
checks but does not replace this device acceptance.

Port `11451` serves the UI, APIs, terminal WebSocket, and workspace preview
gateway. The local backend runs terminal and task processes under the same
OS account as Jahaziel. It is for trusted users only; see the
[threat model](../security/threat-model.md).
