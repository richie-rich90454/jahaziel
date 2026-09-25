<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# VS Code OSS stays the IDE server base

- Status: accepted
- Date: 2026-09-25

The platform must serve the IDE from a browser on every supported host,
including `win32-x64` and `win32-arm64`, because the local backend runs
workspaces as ordinary host processes and the REH is launched beside
them.

The maintained third-party browser distributions were evaluated against
that single hard requirement:

- **coder/code-server** publishes no Windows releases. Installing from
  npm on Windows is explicitly unsupported and is tracked as broken.
- **gitpod-io/openvscode-server** publishes Linux-only releases; native
  Windows is an open feature request, with WSL as the suggested path.
- **eclipse-openvsx** is an extension registry, not a VS Code server.

VS Code OSS ships its own `vscode-reh-web-win32-x64-min` and
`vscode-reh-web-win32-arm64-min` targets, so it is the only base that
covers all six variants the runtime selects. Adopting a Linux-only
distribution would silently drop the Windows IDE rather than fix it.

The cost of this choice is that the fork owns a patch stack, and the
stack must be rebased whenever the pinned upstream tag moves. That work
is tracked in `docs/ide/patch-manifest.md`. Telemetry, AI features, and
Microsoft endpoints stay removed by that stack, which is why the fork
cannot be replaced by a stock upstream build either.
