<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Environment variables

Last verified against version 0.1.0-SNAPSHOT.

Values come from the jar-adjacent `.env` and the real environment. **Real
environment variables win over `.env`.** Configuration is read once at startup,
so changing a credential requires a restart; there is no hot dual-secret
window. See the [configuration guide](../guide/configuration.md) for prose and
the [downstream integration guide](../basis_auth_setup_guide.md#41-configuration)
for the contract.

## Canonical basis-auth set (required)

Use these spellings for new installs. They match
[section 4.1](../basis_auth_setup_guide.md#41-configuration):

| Variable | Purpose |
|---|---|
| `BASIS_AUTH_ISSUER` | OIDC issuer URL |
| `BASIS_AUTH_CLIENT_ID` | Registered client id |
| `BASIS_AUTH_CLIENT_SECRET` | Client secret (confidential clients) |
| `BASIS_AUTH_RESOURCE` | Resource audience |
| `BASIS_AUTH_RESOURCE_SECRET` | Resource secret for webhook proof and internal API authentication |
| `BASIS_AUTH_INTERNAL_URL` | Base URL for internal API calls |
| `BASIS_AUTH_COOKIE_SECRET` | App session cookie secret; at least 32 bytes |
| `CURRENT_URL_ORIGIN` | Public HTTPS origin with no trailing path |

The two callback URLs are derived from `CURRENT_URL_ORIGIN`:

- Login callback: `${CURRENT_URL_ORIGIN}/api/auth/basis/callback`
- Backchannel receiver: `${CURRENT_URL_ORIGIN}/api/auth/basis/backchannel`

Registered redirect URIs must match the callback **exactly**. The backchannel
URL must also be registered and verified for the resource; basis-auth failure
code `14408` means it is not. See
[error codes](./errors.md#numeric-basis-auth-codes).

Legacy `BASIS_*` names (`BASIS_CLIENT_ID`, `BASIS_CLIENT_SECRET`,
`BASIS_RESOURCE`, `BASIS_RESOURCE_SECRET`, `BASIS_INTERNAL_URL`,
`BASIS_SCOPES`, `BASIS_OFFLINE_ACCESS`) remain accepted as aliases only. Do not treat
`BASIS_AUTH_SCOPES`, `BASIS_AUTH_OFFLINE_ACCESS`,
`BASIS_ADMIN_BOOTSTRAP_EMAIL`, `BASIS_ADMIN_REQUIRED_ACR`, or permission
settings as part of canonical setup; older examples may still mention them.
Secret rotation is covered in the guide's
[secret rotation](../guide/configuration.md#secret-rotation) section and
sections 10.4/10.5 of the downstream guide.

## Optional Microsoft Entra keys

| Variable | Purpose |
|---|---|
| `MICROSOFT_TENANT_ID` | Entra tenant when the provider needs one explicitly |
| `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET` | Entra application credentials |
| `MICROSOFT_WEBHOOK_STATE`, `MICROSOFT_WEBHOOK_ID` | Webhook state and identifier |

## Other login providers

`AUTH_PROVIDERS` (dotted form `auth.providers`) selects which providers are
enabled. Each plugin reads only the keys it needs; configure the provider you
actually use.

| Provider | Variables |
|---|---|
| Microsoft Entra | `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, `MICROSOFT_ISSUER`, `MICROSOFT_REDIRECT_URI`, `MICROSOFT_SCOPES` |
| Google | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_ISSUER`, `GOOGLE_REDIRECT_URI`, `GOOGLE_SCOPES` |
| Generic OIDC | `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_ISSUER`, `OIDC_REDIRECT_URI`, `OIDC_SCOPES` |
| GitHub | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_SCOPES`, plus `GITHUB_AUTHORIZE_URL`, `GITHUB_TOKEN_URL`, `GITHUB_USER_URL`, `GITHUB_EMAILS_URL` |
| Passkey (WebAuthn) | `PASSKEY_ORIGIN`, `PASSKEY_RP_ID`, `PASSKEY_RP_NAME` |
| API token | `API_TOKEN_TTL_MILLIS`, `API_TOKEN_ISSUER` |
| LDAP | `LDAP_URL`, `LDAP_BASE_DN`, `LDAP_BIND_DN`, `LDAP_BIND_PASSWORD`, plus `LDAP_SUBJECT_ATTRIBUTE`, `LDAP_EMAIL_ATTRIBUTE`, `LDAP_DISPLAY_NAME_ATTRIBUTE`, `LDAP_MFA_ATTRIBUTE`, `LDAP_MFA_VALUE`, `LDAP_USER_FILTER` |
| Local password | `LOCAL_USERNAME`, `LOCAL_PASSWORD`, `LOCAL_EMAIL` |

Full attribute and filter details are in the
[configuration guide](../guide/configuration.md#authentication-providers).

## Authentication and ops settings

These are `application.yml`-style properties; supply them as environment
variables with the dotted name uppercased where your launcher requires it.

| Setting | Default | Purpose |
|---|---|---|
| `jahaziel.auth.plugin-identity-ttl` | `15m` | How long an unexpiring plugin identity may back a browser session |
| `jahaziel.ops.drain-timeout-ms` | `15000` | Graceful drain timeout |
| `jahaziel.ops.webhook-url`, `jahaziel.ops.webhook-secret` | empty | Outbound ops webhook target and secret |
| `jahaziel.ops.backup-interval-ms` | `86400000` | Scheduled backup interval |
| `jahaziel.ops.rate-limit-per-minute` | `5000` | Request rate-limit override |
| `jahaziel.ops.quota-sessions` | `50` | Session quota |
| `jahaziel.ops.log-max-bytes` | `10485760` | Structured log file size cap |
| `jahaziel.snapshot.auto-retention-count` | `30` | Retained daily snapshots |
| `jahaziel.snapshot.pre-delete-retention-days` | `90` | Snapshots kept after workspace deletion |

## Updates and offline mode

| Variable / flag | Purpose |
|---|---|
| `JAHAZIEL_UPDATE_CHANNEL` | `stable` (default, pinned verified jar) or `latest` (tracks newest published jar; needs network) |
| `--offline` | Run flag forbidding tool downloads; `capabilities` then reports `opencode`, `ripgrep`, `language-packs` and `updates` unavailable |
| `JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE` | Node.js path for TypeScript/Python language packs (default `/usr/bin/node`) |

## Runtime and deployment

| Variable | Purpose |
|---|---|
| `PORT` | API port when `--port` is absent (default `11451`) |
| `HOST` | Optional bind host used by deployment templates |
| `TRUSTED_PROXY_HOPS` | Optional trusted reverse-proxy hop count |
| `DISABLE_DEBUG_ROUTES` | Optional switch to disable debug routes |
| `JAHAZIEL_GATEWAY_PORT` | Legacy standalone gateway socket when `--gateway-port` is absent (default `8081`; off unless enabled) |
| `JAHAZIEL_DOMAIN` | Public domain and token issuer when `--domain` is absent (`localhost` by default) |
| `JAHAZIEL_LOG_LEVEL` | Root log level |
| `JAHAZIEL_DEMO` | Legacy demo flag (prefer `--demo`) |
| `JAHAZIEL_BACKEND` | `local` (the only selectable backend in this release) |
| `JAHAZIEL_SHARE_SECRET` | HMAC secret for share links; generated in memory if unset |
| `JAHAZIEL_JWT_TTL_SECONDS` | Preview/session token lifetime (`jahaziel.jwt.ttl-seconds`, default `900`) |
| `NO_COLOR` | Disables ANSI colors in `logs` output |
| `DATABASE_POOL_MAX` | Optional pool size when a pooling datasource is configured |

Jahaziel uses **SQLite** by default (`./data/jahaziel.db`). Do not introduce a
PostgreSQL `DATABASE_URL`; a pool setting applies only where a pooling
datasource is actually configured.
