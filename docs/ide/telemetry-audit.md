<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Telemetry audit

Every outbound channel in the IDE tree and what the fork does about it.

| Channel | Upstream behavior | Fork action |
|---|---|---|
| `vscode-telemetry` (Azure Application Insights) | Usage data to Microsoft | Replaced with no-op module |
| `@vscode/telemetry` (workbench) | Usage data to Microsoft | Replaced with no-op |
| `product.json` `aiConfig` | Telemetry configuration | Removed |
| `product.json` `enableTelemetry` | Telemetry switch | Forced `false` |
| `product.json` `telemetryOptOutUrl` | Opt-out page | Removed |
| `product.json` `crashReporter` | Crash uploads | Removed |
| Update endpoints | Version checks to Microsoft | Removed with autoupdate |
| Marketplace telemetry | Install metrics | Open VSX only, no Microsoft endpoints |
| Extension install telemetry | Install metrics | Removed |
| Settings Sync | Microsoft-hosted sync | Provider removed |
| Crash reporter process | Crash uploads | Removed |
| `ExperimentsService` | A/B testing assignments | Registration removed |

Verification: `ide/tests/no-network-test/` starts the IDE with only
Open VSX reachable and fails on any connection to the blocklist.
