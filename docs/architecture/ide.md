<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# IDE

Last verified against version 0.1.0-SNAPSHOT.

Jahaziel Code is a fork of VS Code with telemetry, AI features, and
Microsoft endpoints removed; the marketplace is Open VSX only.
Details of each change: `docs/ide/patch-manifest.md`. Outbound
channels and their removal: `docs/ide/telemetry-audit.md`.

The IDE server (REH) ships in three modes; see `docs/plan.md`:

- `fat`: server bundled in the jar; works offline.
- `slim`: uses host-installed tools; smallest distribution.
- `download`: fetched once at first run, cached under `./data/`.

## Why the fork stays on VS Code OSS

The IDE must run as a browser-served server on every supported host,
including `win32-x64` and `win32-arm64`, because the local backend runs
workspaces as host processes.

The maintained third-party browser distributions were evaluated and
rejected for that reason:

| Distribution | Windows build | Verdict |
|---|---|---|
| `coder/code-server` | None published; npm install is unsupported and known-broken | Rejected: no `win32` artifact |
| `gitpod-io/openvscode-server` | Releases are Linux-only; native Windows is unsupported | Rejected: no `win32` artifact |
| `eclipse-openvsx` | Extension registry, not a VS Code server | Not applicable |

VS Code OSS ships its own `vscode-reh-web-win32-*-min` target, so the
fork is the only option that covers all six variants. The decision is
recorded in `docs/contributing/architecture-decisions/ide-server-base.md`.

## What the runtime requires of a build artifact

`IdeService` verifies and extracts exactly one shape, so `ide/build.sh`
produces it:

| Item | Value |
|---|---|
| Tarball | `data/ide/jahaziel-code-server-<slug>.tar.zst` |
| Checksum | `data/ide/jahaziel-code-server-<slug>.tar.zst.sha256` (required; a missing sidecar fails closed) |
| Extracted top-level directory | `<slug>`, for example `win32-x64` |
| Launcher | `<slug>/bin/jahaziel-code-server.cmd` on Windows, `<slug>/bin/jahaziel-code-server` elsewhere |
| Slug | `win32-x64`, `win32-arm64`, `linux-x64`, `linux-arm64`, `darwin-x64`, `darwin-arm64` |

The launcher name follows `product.json` `serverApplicationName`, which
patch 0001 sets to `jahaziel-code-server`; the upstream packaging task
renames its generated script to match.

`doctor` checks the host variant, because the local backend serves the
IDE from the same machine. On a Linux server that is `linux-x64`.
