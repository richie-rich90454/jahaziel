<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Workspaces

A workspace is one developer's project home plus its lifecycle state and
explicitly started processes. Each workspace has its own home at
`data/workspaces/{workspace-id}/home` beneath the configured jar-adjacent data
directory. Other persisted state (SQLite, keys, language packs, snapshots,
outbox, logs) stays under that same data root.

## Core lifecycle and UI-wired workbench

- Lifecycle (`create/start/stop/destroy`) is owned by
  `dev.jahaziel.workspace` and `dev.jahaziel.runtime`. The default `local`
  backend prepares a real home directory; it does not start an idle placeholder
  process. Terminals, setup steps, and run tasks launch processes only when
  requested.
- Native workspace processes run as the Jahaziel service account and share that
  account's permissions. This is for trusted users and does not provide hostile
  multi-tenant isolation. The Firecracker backend is retained for separate
  verification and is not selectable in the release; no KVM isolation is
  claimed until a complete boot and recovery path passes native Linux/KVM
  acceptance.
- **Files** are served by authenticated workspace-rooted APIs with relative
  paths, traversal and symlink-escape checks, strong content ETags, and
  `If-Match` on existing-file saves. The workbench explorer, editor tabs,
  upload/trash/bulk actions, and clone/git panels are wired to those APIs.
- **Tasks** come from server-managed manifests plus discovered project scripts
  (npm/yarn/pnpm, Make, Maven/Gradle, Cargo, Go). The Run panel lists, starts,
  stops, and streams bounded output; the server owns the command vector and
  injects `JAHZIEL_PREVIEW_BASE_PATH` where supported.
- **Terminal** sessions are created explicitly. `dev.jahaziel.terminal` owns
  PTYs and an authenticated WebSocket handshake bound to the workspace and
  session; the browser cannot pass an executable or argv. The terminal panel
  supports input, resize, history, close, and manual reconnect, with a touch
  key strip and explicit copy action.
- **Preview** ports are registered from server-owned templates and, when
  enabled, from candidates discovered in a tracked process tree. A user must
  explicitly register a candidate before it is opened. Routing uses the
  workspace-scoped gateway on port `11451` and the fixed
  `preview.<JAHAZIEL_DOMAIN>` host at `/{workspaceId}/{port}/`.
- **Snapshots** are created and retained by `dev.jahaziel.snapshot`. The
  workbench History panel lists snapshots and the file-history controls in the
  Data panel can compare and restore versions.
- Run tasks, setup steps, and language servers start in dedicated Linux
  sessions. The process registry records executable identity, start time, and
  session ID so startup recovery can stop the whole session without targeting
  a recycled PID.

## Service panels wired in the workbench

The former backend-only modules now have authenticated panels in `web/src`,
implemented as deep-module REST clients over `/api/v1`. Each panel reports
explicit availability when its host endpoints are absent, so a deployment can
omit a module without breaking the workbench.

- **Collaboration** (`dev.jahaziel.collaboration`): members and roles,
  invitations, presence, file comments, live sessions, persisted share links
  with revoke, and collaboration audit.
- **Agent** (`dev.jahaziel.agent`, `dev.jahaziel.agenttools`): sessions and
  transcripts, prompt snippets, staged unified-patch review with per-hunk
  accept/reject, chat, model catalog, and usage. The coding-agent CLI (OpenCode)
  is managed per user through an authenticated PTY.
- **Data tools** (`dev.jahaziel.datatools`): SQLite browsing and queries, CSV
  parse/write/save, notebook cells and execution, search index, and file
  version history/restore.
- **Club** (`dev.jahaziel.club`): sessions and join codes, tutorial progress,
  checkpoints, showcase, chat, moderation, and help queues.
- **Ops** (`dev.jahaziel.ops`, `dev.jahaziel.audit`): support bundles, backup
  status, audit query/export, GDPR export/delete, and approvals. Diagnostics
  and health are also available through the admin page and CLI.

Cookie-authenticated writes require a matching browser origin, and every panel
call rechecks the caller's workspace authorization on the server. See the
[threat model](../security/threat-model.md) for the trust boundary and the
[workbench guide](../guide/using-jahaziel.md) for the user-facing contract.
