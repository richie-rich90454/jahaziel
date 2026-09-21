<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Workspaces

A workspace is one developer's home directory plus its processes,
reachable through the browser via the IDE and the terminal.

- Lifecycle (`create/start/stop/destroy`) is owned by
  `dev.jahaziel.workspace` and `dev.jahaziel.runtime`.
- Every workspace lives under `./data/workspaces/`; nothing is
  written outside `./data/`.
- Two `NodeBackend` implementations: local processes (every OS) and
  Firecracker microVMs (Linux with KVM). The local backend runs
  workspaces as host processes with no isolation boundary; see
  `docs/security/threat-model.md`.
- Ports, snapshots, and sync ride the loopback gateway on port 11451.
