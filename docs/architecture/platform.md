<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platform

Last verified against version 0.1.0-SNAPSHOT.

`dev.jahaziel.platform` contains host detection and platform-specific
helpers. The same jar runs on Linux, macOS, and Windows; Ubuntu 26.04 LTS on
Linux x64 and arm64 is the CI-verified reference host. Real-host acceptance on
macOS and Windows is still open. iPad is the primary browser client; Windows,
macOS, and Linux desktop browsers are also clients and may host the server.

The iPad workbench must remain usable at compact widths with touch-only input
and with an external keyboard and trackpad. Device acceptance covers Safari,
Chrome, and Edge across the current and two previous major iPadOS versions.
This product target does not imply that desktop CI alone qualifies a release.

- OS and arch detection lives here and nowhere else.
- `dev.jahaziel.platform.spi` supplies the per-OS process environment (shell,
  path, and environment variables) as a plugin.
- Per-OS port discovery plugins live in `dev.jahaziel.networking.internal`.
- Supervisors are per OS: systemd, launchd, and a WinSW Windows service wrapper;
  see the [service templates](../deployment/single-server.md#service-templates-per-os).
- The supported server targets and native x64/arm64 CI matrix are listed in
  `docs/plan.md`.
