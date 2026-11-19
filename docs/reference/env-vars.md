<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Environment variables

Last verified against version 0.1.0-SNAPSHOT.

basis-auth credentials accept two spellings: the canonical `BASIS_AUTH_*` names
(used by the [downstream integration guide](../basis_auth_setup_guide.md#41-configuration))
and the legacy `BASIS_*` aliases from earlier `.env` files. Both are equivalent;
the dotted names in parentheses are the `.env`/`application.yml` form. All values
are read once at startup.

| Variable | Used for |
|---|---|
| `BASIS_AUTH_ISSUER` | DevConnect issuer (`basis.auth-issuer`) |
| `BASIS_REDIRECT_URI` | Callback URL (`basis.redirect-uri`) |
| `BASIS_AUTH_CLIENT_ID`, `BASIS_CLIENT_ID` | Client id (`basis.client-id`); `BASIS_CLIENT_ID` is the legacy alias |
| `BASIS_AUTH_CLIENT_SECRET`, `BASIS_CLIENT_SECRET` | Client secret (`basis.client-secret`); empty means public client |
| `BASIS_AUTH_RESOURCE`, `BASIS_RESOURCE` | Audience (`basis.resource`) |
| `BASIS_AUTH_RESOURCE_SECRET`, `BASIS_RESOURCE_SECRET` | Resource secret for webhook proof and internal API authentication (`basis.resource-secret`) |
| `BASIS_AUTH_INTERNAL_URL` | Base URL for internal API calls (`basis.internal-url`); only when calling the internal API |
| `BASIS_AUTH_SCOPES` | Scopes to request from the IdP (`basis.scopes`) |
| `BASIS_AUTH_OFFLINE_ACCESS` | Request offline access so the IdP issues a refresh token (`basis.offline-access`; default `false`) |
| `BASIS_ADMIN_BOOTSTRAP_EMAIL` | Verified email that receives `ROLE_ADMIN` on first verified login (`basis.admin-bootstrap-email`) |
| `BASIS_ADMIN_REQUIRED_ACR` | ACR value proving MFA for the bootstrapped admin (`basis.admin-required-acr`) |
| `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, `MICROSOFT_ISSUER`, `MICROSOFT_REDIRECT_URI`, `MICROSOFT_SCOPES` | Microsoft Entra ID provider plugin |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_ISSUER`, `GOOGLE_REDIRECT_URI`, `GOOGLE_SCOPES` | Google provider plugin |
| `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_ISSUER`, `OIDC_REDIRECT_URI`, `OIDC_SCOPES` | Generic OIDC provider plugin |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_SCOPES` | GitHub provider plugin |
| `PASSKEY_ORIGIN`, `PASSKEY_RP_ID`, `PASSKEY_RP_NAME` | Passkey (WebAuthn) provider plugin |
| `API_TOKEN_TTL_MILLIS`, `API_TOKEN_ISSUER` | API token provider plugin |
| `LDAP_URL`, `LDAP_BASE_DN`, `LDAP_BIND_DN`, `LDAP_BIND_PASSWORD` | LDAP provider plugin (plus attribute/filter overrides in the [configuration guide](../guide/configuration.md#authentication-providers)) |
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
[configuration guide](../guide/configuration.md). For client and resource secret
rotation, see the guide's
[secret rotation](../guide/configuration.md#secret-rotation) section and the
downstream guide sections 10.4/10.5.
