<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Configuration

Last verified against version 0.1.0-SNAPSHOT.

`.env` lives beside the jar. Real environment variables always win over
`.env` values. Login stays off until all five `BASIS_*` values are set.

| Key | Default | Meaning |
|---|---|---|
| `BASIS_AUTH_ISSUER` | — | DevConnect IdP issuer URL |
| `BASIS_REDIRECT_URI` | — | Registered callback URL |
| `BASIS_CLIENT_ID` | — | Confidential client id |
| `BASIS_CLIENT_SECRET` | — | Confidential client secret (`init` generates one) |
| `BASIS_RESOURCE` | — | API audience |
| `PORT` | `11451` | API port (`--port` overrides) |
| `JAHAZIEL_GATEWAY_PORT` | `8081` | Legacy standalone gateway socket (`--gateway-port` overrides; off unless enabled) |
| `JAHAZIEL_DOMAIN` | `localhost` | Public domain for gateway URLs and token issuer (`--domain` overrides; browsers resolve `*.localhost` to loopback, so local runs need no setup; set a real domain with DNS for LAN/server runs) |
| `JAHAZIEL_LOG_LEVEL` | `INFO` | Root log level |
| `JAHAZIEL_DEMO` | `false` | Legacy demo flag (prefer `--demo`) |
| `JAHAZIEL_BACKEND` | `local` | `local` or `firecracker` (Linux with `/dev/kvm`) |

Flags: `--port=N`, `--gateway-port=N` (default `8081`),
`--domain=HOST`, `--data-dir=PATH`, `--demo`. See the
[CLI reference](../reference/cli.md).
