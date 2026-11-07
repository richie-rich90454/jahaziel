<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platform

Last verified against version 0.1.0-SNAPSHOT.

`dev.jahaziel.platform` contains host detection and platform-specific
helpers. Release servers target Linux x64 and arm64. Windows and macOS
are browser clients, not supported server hosts for this release.

- OS and arch detection lives here and nowhere else.
- Per-OS process, filesystem, and socket behavior is adapted here.
- The supported server targets and native x64/arm64 CI matrix are listed in
  `docs/plan.md`.
