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
