<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Workspaces

A workspace is one developer's project home plus its lifecycle state and
explicitly started processes. The home is separate for each workspace and is
stored at `data/workspaces/{workspace-id}/home` beneath the configured data
directory.

- Lifecycle (`create/start/stop/destroy`) is owned by
  `dev.jahaziel.workspace` and `dev.jahaziel.runtime`.
- The default `local` backend prepares a real home directory. It does not
  start an idle placeholder process. Terminals, setup steps, and run tasks
  launch processes only when requested.
- Native workspace processes run as the Jahaziel service account and share
  that account's permissions. This is for trusted users and does not provide
  hostile multi-tenant isolation.
- Run tasks, setup steps, and language servers start in dedicated Linux
  sessions. The process registry records executable identity, start time, and
  session ID so startup recovery can stop the whole session without targeting
  a recycled PID. Terminal sessions use the PTY-created Linux session.
- The optional Firecracker backend remains experimental and requires separate
  Linux/KVM acceptance before it can be treated as a supported release mode.
- Workspace files, task state, packs, and application state stay beneath the
  configured data directory. Ports and previews use the gateway on port 11451.

See the [threat model](../security/threat-model.md) and
[workspace guide](../guide/using-jahaziel.md) for the user-facing contract.
