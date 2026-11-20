<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Configuration

Last verified against version 0.1.0-SNAPSHOT.

`.env` lives beside the jar. Real environment variables always win over
`.env` values. Login is off until at least one authentication provider plugin is
configured; the `BASIS_*` values below configure the reference basis-auth
provider, not every possible login source.

| Key | Default | Meaning |
|---|---|---|
| `BASIS_AUTH_ISSUER` | — | basis-auth issuer URL |
| `BASIS_AUTH_CLIENT_ID` | — | Confidential client id |
| `BASIS_AUTH_CLIENT_SECRET` | — | Confidential client secret (`init` generates one); empty means public client |
| `BASIS_AUTH_RESOURCE` | — | API audience |
| `BASIS_AUTH_RESOURCE_SECRET` | — | Resource secret used for webhook proof and internal API authentication |
| `BASIS_AUTH_INTERNAL_URL` | — | Private listener for webhook verification and user-state checks |
| `BASIS_AUTH_COOKIE_SECRET` | — | At least 32 random bytes; accepted for the canonical setup (sessions are server-side, tokens signed with `data/keys`) |
| `CURRENT_URL_ORIGIN` | — | Public HTTPS origin, no trailing path; derives the callback and backchannel URLs |
| `PORT` | `11451` | API port (`--port` overrides) |
| `JAHAZIEL_GATEWAY_PORT` | `8081` | Legacy standalone gateway socket (`--gateway-port` overrides; off unless enabled) |
| `JAHAZIEL_DOMAIN` | `localhost` | Public domain for gateway URLs and token issuer (`--domain` overrides; browsers resolve `*.localhost` to loopback, so local runs need no setup; set a real domain with DNS for LAN/server runs) |
| `JAHAZIEL_LOG_LEVEL` | `INFO` | Root log level |
| `JAHAZIEL_DEMO` | `false` | Legacy demo flag (prefer `--demo`) |
| `JAHAZIEL_BACKEND` | `local` | `local` is the only selectable backend in this release; Firecracker is retained for separate verification and is not selectable |
| `JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE` | `/usr/bin/node` | Existing Node.js executable used by explicitly enabled TypeScript and Python language services; Jahaziel never installs it |
| `JAHAZIEL_SHARE_SECRET` | — | HMAC secret for share links; generated in memory if unset, so revocation and signatures are lost on restart |

basis-auth uses the lean canonical set in the
[downstream integration guide](../basis_auth_setup_guide.md#41-configuration):
the eight keys above, with `CURRENT_URL_ORIGIN` deriving
`/api/auth/basis/callback` and `/api/auth/basis/backchannel`. The old
`BASIS_REDIRECT_URI`, `BASIS_CLIENT_ID`, `BASIS_CLIENT_SECRET` and
`BASIS_RESOURCE` names remain as backward-compatible aliases but are not the
canonical setup. The dotted `basis.*` names are the `.env`/`application.yml`
form of the same settings. All values are read once at startup, so a credential
change requires a restart.

Flags: `--port=N`, `--gateway-port=N` (default `8081`),
`--domain=HOST`, `--data-dir=PATH`, `--demo`. See the
[CLI reference](../reference/cli.md).

## Authentication providers

Sign-in is plugin-based. `dev.jahaziel.plugin` provides the kernel;
`dev.jahaziel.identity.auth` defines `AuthProviderPlugin` and an ordered
`AuthPluginRegistry`. OAuth/basis-auth is one plugin among many.

Providers are resolved in this priority order; the first enabled and configured
provider that can authenticate the request wins:

| Priority | Provider | Plugin name |
|---|---|---|
| 1 | basis-auth (DevConnect/Basis OIDC) | `basis-auth` |
| 2 | Microsoft Entra ID, Google, GitHub | `microsoft`, `google`, `github` |
| 3 | Generic OIDC | `oidc` |
| 4 | Local password + TOTP | `local` |
| 5 | Passkey (WebAuthn) | `passkey` |
| 6 | Other | `api-token`, `ldap` |

Configuration keys are namespaced per provider. Dotted keys come from `.env` or
`application.yml`; uppercase keys are the equivalent environment variables.

| Provider | Required | Optional |
|---|---|---|
| `basis-auth` | `BASIS_AUTH_ISSUER`, `BASIS_AUTH_CLIENT_ID`, `BASIS_AUTH_CLIENT_SECRET`, `BASIS_AUTH_RESOURCE`, `CURRENT_URL_ORIGIN` | `BASIS_AUTH_RESOURCE_SECRET`, `BASIS_AUTH_INTERNAL_URL`, `BASIS_AUTH_COOKIE_SECRET`; legacy `BASIS_REDIRECT_URI` / `BASIS_CLIENT_ID` / `BASIS_CLIENT_SECRET` / `BASIS_RESOURCE` aliases |
| `microsoft` | `microsoft.client-id` / `MICROSOFT_CLIENT_ID` | `microsoft.client-secret`, `microsoft.redirect-uri`, `microsoft.issuer`, `microsoft.scopes` |
| `google` | `google.client-id` / `GOOGLE_CLIENT_ID` | `google.client-secret`, `google.redirect-uri`, `google.issuer`, `google.scopes` |
| `oidc` | `oidc.client-id` / `OIDC_CLIENT_ID` | `oidc.client-secret`, `oidc.redirect-uri`, `oidc.issuer`, `oidc.scopes` |
| `github` | `github.client-id` / `GITHUB_CLIENT_ID` | `github.client-secret`, `github.scopes`, `github.authorize-url`, `github.token-url`, `github.user-url`, `github.emails-url` |
| `local` | `local.username`, `local.password` | `local.email` |
| `passkey` | `passkey.origin`, `passkey.rp-id` | `passkey.rp-name` |
| `api-token` | — | `api-token.ttl-millis`, `api-token.issuer` |
| `ldap` | `ldap.url`, `ldap.base-dn` | `ldap.subject-attribute`, `ldap.email-attribute`, `ldap.display-name-attribute`, `ldap.mfa-attribute`, `ldap.mfa-value`, `ldap.user-filter`, `ldap.bind-dn`, `ldap.bind-password` |

Identities are linked by **(provider, subject)** — `AuthIdentity.linkKey()` is
`provider + ":" + subject`. Email is profile data only and never links accounts
on its own. The `local` provider grants `ROLE_ADMIN` only after TOTP enrollment;
OIDC providers assert MFA through IdP claims.

### Secret rotation

To rotate the client secret, coordinate with the operator, deploy the new value
to every environment, verify login and refresh, then retire the old secret. To
rotate the resource secret, distribute the new value to every caller — the
backchannel receiver, internal API clients, and any other service authenticating
as the resource — before retiring the old one. The full sequences and failure
modes are in the downstream guide's
[client secret rotation](../basis_auth_setup_guide.md#104-rotate-a-client-secret)
and [resource secret rotation](../basis_auth_setup_guide.md#105-rotate-a-resource-secret)
sections. basis-auth reads configuration once at startup, so **restart Jahaziel
after changing either secret**; a running process keeps using the old value.

## Debug adapters

Debugging is disabled for a language until an administrator configures a
compatible Debug Adapter Protocol (DAP) adapter. Jahaziel does not download or
install debugger adapters. Add the catalog to the external `application.yml`
in the directory from which the jar is launched, and provide an absolute Linux
executable path plus the exact fixed arguments verified for that adapter:

```yaml
jahaziel:
  debug:
    adapters:
      node:
        version: "<administrator-pinned-version>"
        transport: STDIO
        command:
          - "/absolute/path/to/verified-node-dap-adapter"
          - "<verified-stdio-argument>"
```

Replace both placeholders with the installed adapter's pinned version and
documented command. Configure only adapters installed and maintained by the
server administrator. The browser cannot select an executable, adapter
arguments, or transport. Add entries using `python`, `java`, `cpp`, `go`, or
`rust` for the other supported language IDs.

`STDIO` is the default and recommended transport. If an adapter supports only
TCP, set `transport: TCP` and include `{port}` in one fixed command argument;
Jahaziel substitutes an available local port. Verify that the adapter binds
only to loopback before enabling TCP. A DAP adapter runs under the Jahaziel
service account with the same trust boundary as workspace processes.

After configuring adapters, restart Jahaziel and open the **Debug** panel in a
running workspace. The Python template creates a workspace-local `.venv` and
installs a pinned `debugpy` package during its visible setup operation. Setup
needs access to PyPI. Configure the server-side Python DAP adapter with a
pinned `debugpy` installation for the same supported release.

The starter templates mark their long-running preview tasks as debuggable.
Python and Java receive dynamically assigned loopback debug ports from
Jahaziel. Node uses its process-attach support. Delve and LLDB attach to live
task process ids for Go, C/C++, and Rust. Linux Yama policy may restrict a
debugger process from attaching to a sibling process, even when both run as
the same service account. Confirm that the server host permits the selected
adapter to attach; see the [Linux kernel Yama documentation](https://docs.kernel.org/admin-guide/LSM/Yama.html).

The workbench can load a deliberately small `.vscode/launch.json` subset. The
document must have version `0.2.0` and a `configurations` array. Launch
entries may use only `name`, `type`, `request`, `program`, `mainClass`,
`cwd`, `args`, and `stopOnEntry`; `request` must be `launch`. Attach entries
may contain only `name`, `type`, and `request`; `request` must be `attach`.
Types map as follows: `node` or `pwa-node`, `python` or `debugpy`, `java`,
`cppdbg`, `go`, and `lldb` (Rust). Java launch uses `mainClass`; other
launch types use `program`. `cwd` and `program` must be workspace-relative
paths. Attach entries select a live Jahaziel task target and cannot specify a
process id, host, port, executable, or command. The loader rejects environment
variables, pre-launch tasks, arbitrary commands, unknown fields, duplicate
names, and absolute or escaping paths.

Select **Load .vscode/launch.json**, then choose a supported entry. Launch
values populate the launch form and are validated again by Jahaziel. Attach
entries select the matching language's live task target. This is a narrow
compatibility subset, not full VS Code launch configuration support.
