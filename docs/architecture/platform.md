<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platform

Last verified against version 0.1.0-SNAPSHOT.

`dev.jahaziel.platform` contains host detection and platform-specific
helpers. Release servers target Linux x64 and arm64, with Ubuntu 26.04 LTS
as the reference host. iPad is the primary browser client; Windows, macOS,
and Linux desktop browsers are also clients, not supported server hosts for
this release.

The iPad workbench must remain usable at compact widths with touch-only input
and with an external keyboard and trackpad. Device acceptance covers Safari,
Chrome, and Edge across the current and two previous major iPadOS versions.
This product target does not imply that desktop CI alone qualifies a release.

- OS and arch detection lives here and nowhere else.
- Per-OS process, filesystem, and socket behavior is adapted here.
- The supported server targets and native x64/arm64 CI matrix are listed in
  `docs/plan.md`.
