<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel

[![Linux build](https://github.com/richie-rich90454/jahaziel/actions/workflows/linux.yml/badge.svg?branch=main)](https://github.com/richie-rich90454/jahaziel/actions/workflows/linux.yml)
[![Apache-2.0 license](https://img.shields.io/github/license/richie-rich90454/jahaziel)](LICENSE)
[![Linux, macOS, Windows](https://img.shields.io/badge/server-Linux%20%2F%20macOS%20%2F%20Windows-2f6f62)](docs/guide/platforms.md)
[![Java 25+](https://img.shields.io/badge/Java-25%2B-2f6f62)](docs/deployment/single-server.md)

**An iPad-first, self-hosted browser IDE for source code stored on your own server.**

Jahaziel gives trusted users persistent workspaces with a browser editor, terminal, run tasks, previews, and optional language diagnostics. The server is one Java application that runs on Linux, macOS, and Windows; developers connect from iPad Safari, Chrome, or Edge, or from a desktop browser, without needing a local IDE or SSH for normal editor sessions.

> [!WARNING]
> Jahaziel runs workspace terminals, tasks, and Git operations as native host processes under the same service account. Use it with trusted users only. It does not provide operating-system isolation between workspaces or hostile multi-tenant security.

## Why Jahaziel

- **Edit server-side source in the browser.** Browse workspace files, edit with CodeMirror 6, and detect conflicting saves.
- **Use a real host shell.** Open an authenticated xterm.js terminal when needed; terminal processes run in the workspace directory with a process environment selected for the host OS.
- **Run and preview projects.** Start template tasks, inspect output, and open workspace-scoped preview links.
- **Work from iPad.** Switch between files, editor, terminal, tasks, and preview with touch-sized controls; use the terminal shortcut strip when a hardware keyboard is not connected.
- **Start from a useful template.** Create separate workspace homes from Node.js, Python, Java, C/C++, Go, Rust, static-site, or empty starters.
- **Use a coding agent in the workspace.** OpenCode is installed once into the user's private Jahaziel data area and can be launched from an authenticated terminal across that user's workspaces; provider sign-in stays in the OpenCode TUI.
- **Add language diagnostics when needed.** Select pinned language packs explicitly; Jahaziel caches them on the server and does not fetch them at startup.
- **Keep source control close to the editor.** Review Git status and diffs, stage or unstage changes, and commit in an existing repository.
- **Keep operations small.** Run one jar on Linux, macOS, or Windows with the host's own supervisor. Workspace files, SQLite state, keys, logs, and optional language packs live under the jar-adjacent data directory.

## What runs where

| Component | Supported target |
|---|---|
| Jahaziel server | Linux x86_64 or arm64, macOS, and Windows; Linux x64 and arm64 are the CI-verified targets |
| Primary browser clients | iPad Safari, Chrome, and Edge; touch-only and keyboard/trackpad workflows |
| Desktop browser clients | Modern browsers on Windows, macOS, and Linux |
| Workbench editor and terminal | CodeMirror 6 and xterm.js in a SolidJS + DaisyUI client, served from the Jahaziel jar |
| Workspace execution | Native host processes under the Jahaziel service account, adapted per OS by `dev.jahaziel.platform.spi` |
| Per-OS supervisor | systemd (Linux), launchd (macOS), or a WinSW Windows service wrapper |

The same `jahaziel.jar` runs on Linux, macOS, and Windows for development and production. Process, filesystem, and port-discovery behavior is selected per host OS; each OS keeps its service definition where that OS requires it. The workflow is unchanged: build locally, then upload the jar and the matching service template to the host. Linux remains the reference production target and the only platform with native CI verification; real-host acceptance on macOS and Windows is still open (see [Project status](#project-status)).

The jar does not bundle a VS Code server or project runtimes. Install only the host runtimes needed for the projects users choose to run. Jahaziel never prefetches language packs; a language pack is downloaded only after someone explicitly selects it, and some packs include a pinned helper runtime. Workspace processes, previews, and terminal tools share the Jahaziel service account, so this release is for mutually trusted teams.

OpenCode is the only coding-agent CLI in the current catalog. Jahaziel provisions it per user on first workspace use, keeps its executable and profile under the configured data directory, and does not share provider credentials between users. Claude Code is unavailable in the current product because the required hosted-product commercial agreement has not been obtained.

## Deployment options and procedures

Pick the option that matches your environment. All options use the **same single executable jar** and **one application port** (default `11451`); only the process supervisor and edge network differ. Build the jar locally and upload it to the host on every OS.

### Option A — Reference production deployment on Linux (recommended)

Ubuntu 26.04 LTS on Linux x64 or arm64, one jar as a `systemd` service under `/var/lib/jahaziel`, with Caddy providing HTTPS on two exact hostnames (one for the IDE, one for previews). This is the documented and CI-verified target. For macOS and Windows, use the launchd and WinSW service templates described in [Deploy Jahaziel on a Linux server](docs/deployment/single-server.md#service-templates-per-os); those hosts have not passed real-host acceptance yet.

The complete, step-by-step procedure — DNS, service account, `.env`, identity provider, `doctor` preflight, systemd unit, Caddyfile, backup, restore, and upgrade — is in **[Deploy Jahaziel on Linux](docs/deployment/single-server.md)**. In summary:

~~~bash
# 1. Install the jar and initialize (as a dedicated service account)
sudo -u jahaziel -- sh -c 'cd /var/lib/jahaziel && java -jar jahaziel.jar init'

# 2. Edit /var/lib/jahaziel/.env with your BASIS_* identity-provider values (see guide)

# 3. Preflight, then run as a service
sudo -u jahaziel -- sh -c 'cd /var/lib/jahaziel && java -jar jahaziel.jar doctor'
sudo systemctl enable --now jahaziel
~~~

The app binds to `127.0.0.1:11451`; Caddy proxies `code.example.com` and `preview.code.example.com` to it and terminates TLS. The proxy must forward WebSockets (terminal, language service, debugger, hot reload). Do not publish port `11451` directly.

### Option B — Direct jar run (single host, no reverse proxy)

Suitable for a private network or evaluation on Linux, macOS, or Windows. Obtain a packaged `jahaziel.jar` from a successful [Linux build](https://github.com/richie-rich90454/jahaziel/actions/workflows/linux.yml) artifact or build it locally (Option D), then:

~~~bash
java -version                       # must be 25 or newer
java -jar jahaziel.jar init          # writes .env and ./data beside the jar
# edit .env: configure at least one authentication provider, PORT, JAHAZIEL_DOMAIN
java -jar jahaziel.jar doctor        # fix every failed check
java -jar jahaziel.jar               # serves http://<host>:<PORT> (default 11451)
~~~

The commands are the same on Windows PowerShell. For a supervised install, register the jar with the host's service template; see [service templates per OS](docs/deployment/single-server.md#service-templates-per-os).

Without Caddy you can reach the IDE directly over HTTP, but previews are served on the configured preview hostname at `/{workspaceId}/{port}/`, so you still need DNS/host routing for previews. For HTTPS and correct preview framing, use Option A.

### Option C — Local demo (loopback only)

Demo mode seeds a sample user and workspace, skips identity-provider setup, and binds to loopback. It is the fastest way to see the UI:

~~~bash
java -jar jahaziel.jar init
java -jar jahaziel.jar --demo
~~~

Open [http://127.0.0.1:11451](http://127.0.0.1:11451). **Never expose demo mode to a network or use it with sensitive data.**

### Option D — Build from source

Linux/macOS (bash):

~~~bash
git clone https://github.com/richie-rich90454/jahaziel.git
cd jahaziel
pnpm --dir web install --frozen-lockfile
pnpm --dir web run build
./mvnw -B verify -Psmoke '-Dtest=!GatewayRelayFilterTest'
# packaged jar: target/jahaziel.jar
~~~

Windows (native PowerShell — no WSL, no global tool installs):

~~~powershell
git clone https://github.com/richie-rich90454/jahaziel.git
cd jahaziel
.\dev.ps1 bootstrap
.\dev.ps1 build
# packaged jar: target\jahaziel.jar  (run on this host or upload to any supported host)
~~~

Requirements (all existing, none installed by the scripts): JDK 25, Node.js 24 or newer, pnpm 12.14.0, and Maven (or the Maven wrapper). `dev.ps1` keeps all Maven and pnpm caches in ignored repository-local directories. Run `.\dev.ps1 cache:report` to see their sizes and `.\dev.ps1 cache:prune --apply` to remove them.

### Host prerequisites

Install on the host only what your projects need. Jahaziel does not install runtimes or tools.

| Requirement | Needed for |
|---|---|
| Java runtime 25 or newer | The server itself, on Linux, macOS, and Windows |
| `git`, `tar`, `curl` | Git panel, backup/restore, tooling |
| `setsid` (util-linux) and `/bin/sh` or `/bin/bash` | Tracked terminal/task process trees on Linux; macOS and Windows use their own process environments |
| `python3` and `python3-venv` | Python and static-site starter templates |
| Node.js, Java JDK, Go, Rust, C/C++ toolchains | Only the templates/tasks/language packs you actually select |

### Configure sign-in and the first administrator

`basis-auth` is one login plugin among several (see [Authentication providers](#authentication-providers)). To use it, register a confidential client with a compatible DevConnect/Basis OIDC identity provider whose callback is exactly `https://code.example.com/api/oauth/callback`. Set these in `.env`:

~~~dotenv
BASIS_AUTH_ISSUER=https://idp.example.com
BASIS_REDIRECT_URI=https://code.example.com/api/oauth/callback
BASIS_CLIENT_ID=your-client-id
BASIS_CLIENT_SECRET=your-client-secret
BASIS_RESOURCE=your-registered-resource
PORT=11451
JAHAZIEL_DOMAIN=code.example.com
JAHAZIEL_LOG_LEVEL=INFO
JAHAZIEL_BACKEND=local

# Optional: grant ROLE_ADMIN to one verified email on first verified login.
# MFA is asserted only when the IdP's amr/acr proves it; no fake MFA is set.
# BASIS_ADMIN_BOOTSTRAP_EMAIL=admin@example.com
# BASIS_ADMIN_REQUIRED_ACR=<idp-mfa-acr-value>

# Optional: persist share-link signing across restarts.
# JAHAZIEL_SHARE_SECRET=<long-random-secret>
~~~

Keep `.env` private (`chmod 0600`). Admin endpoints require both `ROLE_ADMIN` and verified MFA assurance. Without an IdP MFA assurance claim, configure an administrator through your identity provider or database — the product will not silently mark MFA as enrolled. To use Microsoft, Google, GitHub, generic OIDC, local password + TOTP, passkey, API token, or LDAP instead, see [Authentication providers](#authentication-providers).

### Operations (CLI verbs)

~~~bash
java -jar jahaziel.jar doctor                    # preflight
java -jar jahaziel.jar backup                     # back up data (--no-secrets to omit secrets)
java -jar jahaziel.jar restore <file>             # restore a backup
java -jar jahaziel.jar upgrade --to <new-jar>     # offline jar upgrade (--check to inspect)
java -jar jahaziel.jar logs --follow              # application logs
java -jar jahaziel.jar support-bundle             # redacted diagnostics bundle
~~~

See the [CLI reference](docs/reference/cli.md), [backup and restore](docs/operations/backup.md), and [release acceptance checklist](docs/operations/acceptance.md).

## Authentication providers

Login is provider-neutral. The plugin kernel at `dev.jahaziel.plugin` discovers and lifecycle-manages plugins; `dev.jahaziel.identity.auth` defines the `AuthProviderPlugin` contract and the ordered `AuthPluginRegistry`. OAuth/basis-auth is one plugin among many, not the only login source.

Providers are resolved in this priority order — the first enabled and configured provider that can authenticate the request wins:

| Priority | Provider | Plugin name | Notes |
|---|---|---|---|
| 1 | basis-auth | `basis-auth` | DevConnect/Basis OIDC, PKCE, refresh, MFA assurance, backchannel logout |
| 2 | Microsoft Entra ID | `microsoft` | OIDC v2 with tenant-aware issuer |
| 2 | Google | `google` | OIDC |
| 2 | GitHub | `github` | OAuth using the verified primary email |
| 3 | Generic OIDC | `oidc` | Any conforming issuer |
| 4 | Local password + TOTP | `local` | Username/password with TOTP enrollment and MFA assurance |
| 5 | Passkey | `passkey` | WebAuthn credentials |
| 6 | Other | `api-token`, `ldap` | Service tokens and directory authentication |

A plugin is enabled only when it is configured; missing required settings do not silently create a login source.

### Provider configuration keys

Each provider reads its own keys from jar-adjacent `.env`, real environment variables, or the external `application.yml`. Dotted names are the property/`.env` form; uppercase names are the environment form.

| Provider | Required keys | Optional keys |
|---|---|---|
| `basis-auth` | `BASIS_AUTH_ISSUER`, `BASIS_REDIRECT_URI`, `BASIS_CLIENT_ID`, `BASIS_RESOURCE` | `BASIS_CLIENT_SECRET` (confidential clients), `BASIS_RESOURCE_SECRET`, `BASIS_AUTH_INTERNAL_URL`, `BASIS_AUTH_SCOPES`, `BASIS_AUTH_OFFLINE_ACCESS`, `BASIS_ADMIN_BOOTSTRAP_EMAIL`, `BASIS_ADMIN_REQUIRED_ACR` |
| `microsoft`, `google`, `oidc` | `<prefix>.client-id` (`MICROSOFT_CLIENT_ID`, `GOOGLE_CLIENT_ID`, `OIDC_CLIENT_ID`) | `<prefix>.client-secret`, `<prefix>.redirect-uri`, `<prefix>.issuer`, `<prefix>.scopes` |
| `github` | `github.client-id` / `GITHUB_CLIENT_ID` | `github.client-secret`, `github.scopes`, `github.authorize-url`, `github.token-url`, `github.user-url`, `github.emails-url` |
| `local` | `local.username`, `local.password` | `local.email` (admin is granted only after TOTP enrollment) |
| `passkey` | `passkey.origin`, `passkey.rp-id` | `passkey.rp-name` |
| `api-token` | — | `api-token.ttl-millis`, `api-token.issuer` |
| `ldap` | `ldap.url`, `ldap.base-dn` | `ldap.subject-attribute`, `ldap.email-attribute`, `ldap.display-name-attribute`, `ldap.mfa-attribute`, `ldap.mfa-value`, `ldap.user-filter`, `ldap.bind-dn`, `ldap.bind-password` |

Identity linking uses the pair **(provider, subject)**: `AuthIdentity.linkKey()` is `provider + ":" + subject`. Email is profile data only and is never used alone to link accounts, so two providers may assert the same address without colliding. See the [configuration guide](docs/guide/configuration.md#authentication-providers).

## Using Jahaziel

### From Windows

1. Run the server on Linux, macOS, or Windows using Option A, B, or C above.
2. In Windows, open Edge, Chrome, or Firefox and go to `https://code.example.com` (or `http://<host>:11451` for a private direct run on the same host).
3. Sign in through your configured provider. The browser is the only thing you need installed to use a remote server.
4. Use terminal, tasks, and debug panels exactly as on iPad; keyboard/trackpad shortcuts work in desktop browsers.

To develop, rebuild, and host the jar on Windows, use the PowerShell workflow in [Build from source](#option-d--build-from-source), the `dev.ps1` verbs (`build`, `test`, `run`, `dev`, `smoke`, `docs`, `cache:report`, `clean`), and the WinSW service template in [service templates per OS](docs/deployment/single-server.md#service-templates-per-os).

### From iPad

Open Safari, Chrome, or Edge on the current or two previous iPadOS versions. Portrait, landscape, Split View, and Stage Manager are supported targets. The panel bar switches between Files, Editor, Run, Languages, Debug, Git, Terminal, and Preview; the terminal has an on-screen key strip when no hardware keyboard is attached.

### First workspace and everyday workflow

1. **Create a workspace.** On the home page, enter a name and choose a starter (empty, Node.js, Python, Java, C/C++, Go, Rust, or static-site). Each workspace gets its own home directory under the data directory; setup steps and starter files are created server-side with progress and logs, and a failed setup can be retried.
2. **Edit and save files.** Open a file from the tree to edit it in CodeMirror 6. Saving uses ETags with `If-Match`; if the file changed on the server, the workbench shows a conflict with "use server version" / "keep my edits" actions, and local drafts are recoverable after a reload.
3. **Search and quick open.** Use the workspace search, or press Ctrl/Cmd+P for the quick-open palette.
4. **Terminal.** Open a terminal in the workspace home. Sessions persist; after a page reload the workbench reconnects to the most recent session automatically, and history/session selection remains available.
5. **Run and preview.** In the Run panel, start a discovered task. Output and status stream live. Registered ports appear as preview links; use "find preview ports" to discover candidate listeners from tracked task/terminal processes and register one explicitly before opening it. Previews are served on the dedicated preview hostname at `/{workspaceId}/{port}/`.
6. **Debug.** For templates with a debug-capable task, use the Debug panel to launch or attach, set breakpoints by tapping the editor gutter, and step through with stack/variables/watches/console. Adapters are admin-configured; launch settings can be loaded from the supported `.vscode/launch.json` subset.
7. **Language diagnostics.** In the Languages panel, explicitly select a pinned language pack. It is downloaded once into the server cache; diagnostics then appear in the editor. Nothing is fetched at startup.
8. **Coding agent.** From the terminal panel, install and open OpenCode. The executable and profile live under your private data area and are shared across your workspaces; provider sign-in happens inside the OpenCode TUI.
9. **Git.** For an existing repository in the workspace, review status and diffs, stage/unstage, and commit from the Git panel.
10. **Recover after a restart.** Workspace files are never deleted on restart. Interrupted tasks and setup are reconciled and reported, and terminals/sessions reconnect on reload.

More detail: [Quick start](docs/guide/quick-start.md), [Use the workbench](docs/guide/using-jahaziel.md), [Workspace templates](docs/guide/templates.md), [Share links](docs/guide/share-links.md), [Configuration](docs/guide/configuration.md).

## Delivered modules

These modules are present in the jar alongside the core workbench (files, editor, terminal, run, preview, Git, debug, and languages). Modules marked **backend-only** ship their domain/service layer but have no dedicated SolidJS panel at this stage.

| Module | Package | Status |
|---|---|---|
| Collaboration — members, invitations, file comments, share links, live sessions, audit | `dev.jahaziel.collaboration` | Backend-only; JDBC store included |
| Agent assistant — chat, sessions, patch review, prompts, model catalog | `dev.jahaziel.agent` | Backend-only; no dedicated workbench panel yet |
| Agent tools — managed coding-tool service seam | `dev.jahaziel.agenttools` | Backend-only; OpenCode is launched from the terminal |
| Data tools — CSV/SQLite browsing, notebook cells, file diffs, search index | `dev.jahaziel.datatools` | Backend-only |
| Club — teaching sessions, roles, validation tasks, checkpoints | `dev.jahaziel.club` | Backend-only; additive and optional, Git remains the source of truth |
| Observability — metrics, hourly rollups, resource sampling | `dev.jahaziel.monitoring` | Backend-only; health endpoint is available |
| Compliance — audit records, retention, policy evaluation, administration actions | `dev.jahaziel.audit`, `dev.jahaziel.policy`, `dev.jahaziel.administration` | Backend-only |

## Maintainability

- **Plugin seams.** Host, provider, and port-discovery behavior lives behind small contracts (`dev.jahaziel.plugin`, `dev.jahaziel.identity.auth`, `dev.jahaziel.platform.spi`, and the per-OS port discovery plugins) instead of OS/provider branches in feature code.
- **AP CSA-level Java.** New and touched application code favors plain classes, abstract classes, and constructs teachable in AP Computer Science A; a few older classes still use newer syntax. The build still uses JDK 25 and targets Java 25 bytecode.
- **4-space indentation** in Java and in the SolidJS + DaisyUI TypeScript frontend; TypeScript is formatted with `oxfmt`.
- **Small, focused commits.** Prefer single-file commits where the change allows; each commit carries one cohesive behavior with its docs and focused verification.

## Documentation

- [Deploy Jahaziel on Linux](docs/deployment/single-server.md) — includes launchd and Windows service templates
- [Platforms and per-OS supervisors](docs/guide/platforms.md)
- [Quick start](docs/guide/quick-start.md)
- [Use the workbench](docs/guide/using-jahaziel.md)
- [Configure authentication and server settings](docs/guide/configuration.md)
- [Workspace templates](docs/guide/templates.md)
- [Back up and restore data](docs/operations/backup.md)
- [Architecture](docs/architecture/overview.md)
- [Security and trust boundary](docs/security/threat-model.md)
- [CLI reference](docs/reference/cli.md)
- [Environment variables](docs/reference/env-vars.md)
- [Current product plan](docs/plan.md)

## Project status

Jahaziel is under active development. The project version is `0.1.0-SNAPSHOT`; workflow artifacts are build outputs for evaluation, not stable release packages. The Linux x64 and arm64 CI pipeline — including the packaged browser smoke — is green, and a 30-test Windows Playwright E2E suite checks the SolidJS workbench against a local stub. Those are regression checks, not host acceptance: a release still requires real-device iPad testing, native acceptance on both Linux architectures, and real-host checks on macOS and Windows with their service templates. Review the [current plan](docs/plan.md) and [release acceptance checklist](docs/operations/acceptance.md) before deploying it for important work.

## Contribute and get help

Bug reports and feature proposals are welcome. Include the server architecture, Java version, reproduction steps, and relevant redacted logs. Never attach `.env`, private keys, workspace source, or unredacted support bundles.

- [Report a bug](https://github.com/richie-rich90454/jahaziel/issues/new?template=bug_report.md)
- [Request a feature](https://github.com/richie-rich90454/jahaziel/issues/new?template=feature_request.md)
- [Read the contribution guide](CONTRIBUTING.md)

If Jahaziel fits your workflow, star the repository and tell us what you use it for.

## License

Apache License 2.0. See [LICENSE](LICENSE).
