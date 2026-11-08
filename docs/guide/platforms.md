<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platforms

Last verified against version 0.1.0-SNAPSHOT.

Jahaziel servers run on Linux x64 and arm64. Windows, macOS, and Linux
desktops connect through a supported browser; they do not host a Jahaziel
server in this release.

| Server | Arch | Browser clients |
|---|---|---|
| Linux | x64 | Windows, macOS, Linux |
| Linux | arm64 | Windows, macOS, Linux |

Port `11451` serves the UI, APIs, terminal WebSocket, and workspace preview
gateway. The local backend runs terminal and task processes under the same
OS account as Jahaziel. It is for trusted users only; see the
[threat model](../security/threat-model.md).
