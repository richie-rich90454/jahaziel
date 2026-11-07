<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Threat model

## Scope and trust boundary

The first release is for trusted users. Workspace terminals and run tasks execute as native Linux processes under the Jahaziel service account. Those processes can read any files accessible to that account, inspect same-account processes, and use its network access. Workspace-specific file API checks protect requests made through Jahaziel; they do not constrain a native shell process. Do not expose this backend to mutually untrusted users.

Git status, diff, staging, and commit operations also run as the service account. Workbench Git commands clear inherited application environment values, ignore system and global Git configuration, and disable hooks. Repository-local filters can still run with the sanitized environment, and all repository contents remain available to the same service account. Treat repositories and users as trusted; Git operations do not create an OS isolation boundary.

Firecracker is a separately selected backend. Its class being present in the jar is not evidence that KVM, jail setup, command allowlists, or isolation have passed release verification. No hostile multi-tenant guarantee is made.

## Adversaries

| ID | Adversary | Capabilities | Primary defense |
|---|---|---|---|
| A1 | Authenticated workspace user | Edits files and starts allowed terminal/task processes | Workspace-level API authorization; transparent trusted-user limitation |
| A2 | Internet scanner | Reaches the public Jahaziel listener or preview host | Authentication, route authorization, bounded sessions, existing gateway checks |
| A3 | Compromised administrator | Changes templates, users, and policies | Audit events, least-privilege operations, backups |
| A4 | Compromised Jahaziel JVM | Executes as the service account | Host backup and restore; no separate-process security boundary is claimed |
| A5 | Careless user | Deletes or overwrites project files | ETag save conflicts, workspace snapshots, explicit file operations |

## Properties defended

| Property | How |
|---|---|
| Workspace separation through Jahaziel APIs | Per-workspace home paths and workspace authorization on file/process routes |
| Protection against stale editor saves | Strong content ETags and required `If-Match` on existing-file saves |
| Filesystem confinement in file APIs | Relative paths, traversal checks, real-path checks, and symlink escape rejection |
| Git panel scope | Workspace authorization, exact workspace repository-root check, validated relative paths, literal pathspecs, sanitized process environment, disabled hooks, bounded output, and secret redaction |
| Terminal command selection | Server-selected fixed shell; the browser cannot pass an executable or argv |
| Terminal session access | Authenticated WebSocket handshake bound to the authorized session and workspace |
| Credential exposure to PTYs | Environment allowlist; application credentials are not copied into the child environment |
| Optional language tool integrity | Explicit selection, pinned versions and checksums before activation, and server-owned launch and initialization paths |

## Properties not defended

- OS-level isolation between native workspace processes.
- Confidentiality of one workspace from another through the same-account process model.
- Host CPU, memory, process-count, and network isolation for native tasks.
- A compromised Linux kernel, service account, or Jahaziel JVM.
- Hostile multi-tenant workloads. Do not use the default backend for these workloads.

## Attack surface inventory

| Surface | Exposure | Mitigation |
|---|---|---|
| Workspace file API | Authenticated HTTP | Permission check per operation, relative paths, symlink-safe root, text size limit, ETag saves |
| Terminal REST API | Authenticated HTTP | Workspace write permission, global/per-workspace caps, bounded terminal dimensions |
| Terminal stream | Authenticated WebSocket | Handshake authorization, session-bound path, bounded messages, fixed shell |
| Template apply | Authenticated HTTP | Server-managed manifests, bounded resources, unique port and relative starter path validation |
| Run task API | Authenticated HTTP | Template-owned task IDs and server-selected executable/arguments |
| Git API | Authenticated HTTP | Workspace read/write authorization, fixed Git subcommands, bounded output, no clone or remote credential endpoints |
| Language-service stream | Authenticated WebSocket | Allowed language ID, selected pinned server-owned pack, bounded JSON-RPC, workspace-rooted URIs, and server-owned launch and initialization options |
| Workspace preview | Gateway listener | Existing workspace-scoped token and port authorization |
| Cookie-authenticated writes | Authenticated HTTP | Same-origin `Origin` validation and `Sec-Fetch-Site`; bearer-token clients are exempt because their credentials are explicitly attached |
| Application data | Service-account filesystem | Configured data root, backups, diagnostics, file API confinement |

Cookie-authenticated writes require a browser `Origin` that matches the request origin. When
running behind a reverse proxy, only a trusted proxy should set `X-Forwarded-Host` and
`X-Forwarded-Proto`; those headers are used to compare the public origin. A missing or mismatched
origin is rejected. Demo mode grants a fixed administrator identity and therefore binds only to
loopback, even if another bind address is configured.

## Required before hostile multi-tenant use

A future isolated mode must demonstrate per-workspace OS identity or VM isolation, enforced CPU/memory/process/network limits, isolation of shared tool caches, independent audit protection, WebSocket token controls, and native acceptance tests. A backend name or configuration flag alone is not an isolation proof.
