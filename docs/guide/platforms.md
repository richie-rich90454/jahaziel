<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platforms

Last verified against version 0.1.0-SNAPSHOT.

One jar runs everywhere. Only the REH binary and the terminal shell differ.

| OS | Arch | REH variant | Backend | Jar mode |
|---|---|---|---|---|
| Windows | x64 | win32-x64 | local (no isolation) | fat or slim |
| Windows | arm64 | win32-arm64 | local (no isolation) | fat or slim |
| macOS | x64 | darwin-x64 | local (no isolation) | fat or download |
| macOS | arm64 | darwin-arm64 | local (no isolation) | fat or download |
| Linux | x64 | linux-x64 | firecracker (KVM) or local | fat or slim |
| Linux | arm64 | linux-arm64 | firecracker (KVM) or local | fat or slim |

Port is `11451` on all six. The `local` backend runs workspaces as host
processes with no filesystem isolation — see the
[threat model](../security/threat-model.md). All OS branching lives in
`dev.jahaziel.platform`; see
[platform architecture](../architecture/platform.md).
