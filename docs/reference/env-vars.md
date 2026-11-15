<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Environment variables

Last verified against version 0.1.0-SNAPSHOT.

| Variable | Used for |
|---|---|
| `BASIS_AUTH_ISSUER` | DevConnect issuer (`basis.auth-issuer`) |
| `BASIS_REDIRECT_URI` | Callback URL (`basis.redirect-uri`) |
| `BASIS_CLIENT_ID` | Client id (`basis.client-id`) |
| `BASIS_CLIENT_SECRET` | Client secret (`basis.client-secret`) |
| `BASIS_RESOURCE` | Audience (`basis.resource`) |
| `BASIS_ADMIN_BOOTSTRAP_EMAIL` | Verified email that receives `ROLE_ADMIN` on first verified login (`basis.admin-bootstrap-email`) |
| `BASIS_ADMIN_REQUIRED_ACR` | ACR value proving MFA for the bootstrapped admin (`basis.admin-required-acr`) |
| `PORT` | API port when `--port` is absent |
| `JAHAZIEL_GATEWAY_PORT` | Legacy standalone gateway socket when `--gateway-port` is absent |
| `JAHAZIEL_DOMAIN` | Public domain and token issuer when `--domain` is absent (`localhost` by default) |
| `JAHAZIEL_LOG_LEVEL` | Root log level |
| `JAHAZIEL_DEMO` | Legacy demo flag (prefer `--demo`) |
| `JAHAZIEL_BACKEND` | `local` (the only selectable backend in this release) |
| `JAHAZIEL_SHARE_SECRET` | HMAC secret for share links; generated in memory if unset |
| `NO_COLOR` | Disables ANSI colors in `logs` output |

File values come from jar-adjacent `.env`; real environment wins. `--demo`
strips the `BASIS_*` entries before boot. Full prose in the
[configuration guide](../guide/configuration.md).
