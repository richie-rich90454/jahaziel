<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Threat model

## Scope and trust boundary

The first release is for trusted users. Workspace terminals and run tasks
execute as native processes under the Jahaziel service account. Those processes
can read any files accessible to that account, inspect same-account processes,
and use its network access. Workspace-specific file API checks protect requests
made through Jahaziel; they do not constrain a native shell process. Do not
expose this backend to mutually untrusted users.

Git status, diff, staging, and commit operations also run as the service
account. Workbench Git commands clear inherited application environment values,
ignore system and global Git configuration, and disable hooks. Repository-local
filters can still run with the sanitized environment, and all repository
contents remain available to the same service account. Treat repositories and
users as trusted; Git operations do not create an OS isolation boundary.

The Firecracker backend is retained for separate verification and is not
selectable in this release. Its class being present in the jar is not evidence
that KVM, jail setup, command allowlists, or isolation have passed release
verification. No hostile multi-tenant guarantee is made.

## Adversaries

| ID | Adversary | Capabilities | Primary defense |
|---|---|---|---|
| A1 | Authenticated workspace user | Edits files and starts allowed terminal/task processes | Workspace-level API authorization; transparent trusted-user limitation |
| A2 | Internet scanner | Reaches the public Jahaziel listener or preview host | Authentication, route authorization, bounded sessions, existing gateway checks |
| A3 | Compromised administrator | Changes templates, users, and stored policy documents | Audit events, least-privilege operations, backups |
| A4 | Compromised Jahaziel JVM or service account | Executes with full application data access | Host backup and restore; no separate-process security boundary is claimed |
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
| Debugger launch selection | The browser selects a supported language and workspace-relative launch fields; only fixed, absolute adapter commands configured by the server administrator may execute |
| Debugger session access | Workspace authorization on REST operations; authenticated WebSocket handshake bound to the exact authorized workspace and running session; bounded DAP frames |
| Debugger source paths | Workspace-rooted launch paths, symlink escape checks, and source path normalization before messages are returned to the browser |
| Optional language tool integrity | Explicit selection, pinned versions and checksums before activation, and server-owned launch and initialization paths |
| MFA assurance | `AuthIdentity.mfaAssured` is derived from verified provider evidence: signed ID-token `amr`/`acr` claims for OIDC/basis-auth, completed TOTP enrollment for local accounts, passkey assertion, or a configured LDAP attribute. The latest login's status is persisted and re-evaluated on each sign-in; admin operations fail closed without it. Approval/consent is never inferred from an unverified email claim. |
| Secrets at rest | Provider signing keys, local TOTP encryption key, and similar material live under `data/keys` in the jar-adjacent data directory and are protected by file permissions and backups |
| Signed webhooks | Outbound ops events carry an HMAC-SHA256 `sha256=` signature over a canonical JSON body, with a bounded `data/outbox` retry queue and dead-letter directory. The basis-auth backchannel receiver verifies the provider's signature, nonce, and expiry before acting. |
| No secret logging | Authentication, provider, and resource-filter code logs outcomes and error categories only — never tokens, secrets, codes, or raw cookies |
| Persisted, revocable share links | `share_link` rows store only a token hash, scope (`VIEW`/`EDIT`), expiry, one-time/used flags, and revocation state. Links survive restart, are audited on create/redeem/revoke, and the Collaboration panel exposes revoke. When `JAHAZIEL_SHARE_SECRET` is configured the signing key is stable across restarts; otherwise it is ephemeral. |

## Properties not defended

- OS-level isolation between native workspace processes.
- Confidentiality of one workspace from another through the same-account process model.
- Host CPU, memory, process-count, and network isolation for native tasks.
- A compromised Linux kernel, service account, or Jahaziel JVM.
- Hostile multi-tenant workloads. Do not use the default backend for these workloads.
- Isolation of debugger adapters from the Jahaziel service account or other workspaces. An administrator-configured adapter runs as the same Linux account as Jahaziel.
- Audit tamper-proofing. Audit events carry a SHA-256 hash chain (`prev_hash`
  → `hash`) inside the application database. There is no external anchor,
  append-only storage, or independent witness, so an attacker with service-account
  or database write access can rewrite or truncate the chain. Treat audit as a
  forensic aid, not a compliance-grade non-repudiation control.
- Revocation of already-issued stateless app JWTs. The next request filter sees
  the cleared session/expiry; basis backchannel logout ends local sessions and
  cookies but cannot recall a token already presented to another service.
- Policy documents: admin CRUD storage of arbitrary JSON only. `PolicyEvaluator`
  has no production callers and policy JSON is not enforced at runtime.

## Attack surface inventory

| Surface | Exposure | Mitigation |
|---|---|---|
| Workspace file API | Authenticated HTTP | Permission check per operation, relative paths, symlink-safe root, text size limit, ETag saves |
| Terminal REST API | Authenticated HTTP | Workspace write permission, global/per-workspace caps, bounded terminal dimensions |
| Terminal stream | Authenticated WebSocket | Handshake authorization, session-bound path, bounded messages, fixed shell |
| Template apply | Authenticated HTTP | Server-managed manifests, bounded resources, unique port and relative starter path validation, per-OS command selectors |
| Run task API | Authenticated HTTP | Template-owned task IDs and server-selected executable/arguments |
| Git API | Authenticated HTTP | Workspace read/write authorization, fixed Git subcommands, bounded output, no clone or remote credential endpoints |
| Language-service stream | Authenticated WebSocket | Allowed language ID, selected pinned server-owned pack, bounded JSON-RPC, workspace-rooted URIs, and server-owned launch and initialization options |
| Debug-session REST API | Authenticated HTTP | Read/write workspace authorization, server-owned adapter catalog, workspace-rooted launch fields, opaque task-issued attach targets, and bounded session limits |
| Debug-session stream | Authenticated WebSocket | Authenticated principal, browser-origin policy, workspace write authorization, exact session binding, and bounded DAP messages |
| Workspace preview | Gateway listener | Existing workspace-scoped token and port authorization; candidates from tracked process trees must be explicitly registered |
| Share link | Authenticated HTTP | Hash-only persisted token, scope, expiry, one-time use, audited create/redeem/revoke, and login-required redemption returning workspace ID and expiry only |
| Collaboration, agent, data, Club, and ops panels | Authenticated HTTP | Workspace authorization per endpoint, explicit unavailable state when a module is absent, confirmation for destructive ops actions |
| Outbound webhook / backchannel | Signed HTTP | HMAC-SHA256 verification, bounded outbox retries, nonce/expiry checks on inbound proofs |
| Cookie-authenticated writes | Authenticated HTTP | Same-origin `Origin` validation and `Sec-Fetch-Site`; bearer-token clients are exempt because their credentials are explicitly attached |
| Application data | Service-account filesystem | Configured data root, keys under `data/keys`, backups, diagnostics, file API confinement |

Cookie-authenticated writes require a browser `Origin` that matches the request origin. When
running behind a reverse proxy, only a trusted proxy should set `X-Forwarded-Host` and
`X-Forwarded-Proto`; those headers are used to compare the public origin. A missing or mismatched
origin is rejected. Demo mode grants a fixed administrator identity and therefore binds only to
loopback, even if another bind address is configured.

## Identity and the basis-auth contract

Basis-auth is the downstream identity-provider contract: client/resource
registration, ID-token claims and `typ` values, refresh and revocation,
backchannel logout, scopes/permissions, and internal API authentication. It is
specified in the [basis-auth setup guide](../basis_auth_setup_guide.md), which
must be updated in the same change as any implementation change to those
behaviors. Canonical credential keys (`BASIS_AUTH_*`) and legacy `BASIS_*`
aliases are read at startup; a credential rotation requires a restart. Other
built-in providers share the same identity store but never join accounts by
email. Email verification alone grants neither admin nor MFA assurance; admin
access requires provider-attested MFA.

## Required before hostile multi-tenant use

A future isolated mode must demonstrate per-workspace OS identity or VM isolation, enforced CPU/memory/process/network limits, isolation of shared tool caches, independent audit protection, WebSocket token controls, and native acceptance tests. A backend name or configuration flag alone is not an isolation proof.
