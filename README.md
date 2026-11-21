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

All options use the **same single executable jar** and **one application port** (default `11451`). The jar owns everything it needs in a jar-adjacent `data/` directory; only the supervisor definition and the edge proxy live outside it. Pick the path that matches how software reaches your host:

| Path | Use it when | Details |
|---|---|---|
| 1. Packaged jar upload | Normal installs: build on a dev machine or take a CI artifact and copy one file | [single-server.md](docs/deployment/single-server.md#path-1--packaged-jar-upload) |
| 2. Jar under an OS supervisor | Every long-running production install | [single-server.md](docs/deployment/single-server.md#path-2--run-under-an-os-supervisor) |
| 3. Repo clone on the target | Air-gapped hosts, source audit, or local patches | [single-server.md](docs/deployment/single-server.md#path-3--clone-the-repository-on-the-target) |

Paths 1 and 3 both end at the same jar directory. Path 2 is how you keep that jar running in production: it supplies restart-on-failure, boot-at-start, and log capture. Use the [local demo](#local-demo-development-only) only for development.

### Path 1 — Packaged jar upload

On a dev machine, build with the smoke profile or a plain package:

~~~bash
./mvnw -B verify -Psmoke
# or, without the packaged browser smoke checks:
./mvnw -B package -DskipTests -Dmaven.test.skip=true
~~~

On Windows PowerShell use `.\mvnw.cmd` in place of `./mvnw`. The result is `target/jahaziel.jar` (`target\jahaziel.jar` on Windows). Copy that one file to the host:

~~~bash
# Linux / macOS
scp target/jahaziel.jar jahaziel@example-host:/var/lib/jahaziel/jahaziel.jar
# or: rsync -av target/jahaziel.jar jahaziel@example-host:/var/lib/jahaziel/
~~~

~~~powershell
# Windows PowerShell: OpenSSH scp, or a file share
scp .\target\jahaziel.jar jahaziel@example-host:C:/jahaziel/jahaziel.jar
Copy-Item .\target\jahaziel.jar \\example-host\share\jahaziel\jahaziel.jar
~~~

Create `.env` beside the jar on the host, then run:

~~~bash
java -jar jahaziel.jar init          # writes .env and ./data beside the jar
java -jar jahaziel.jar doctor        # fix every failed check
java -jar jahaziel.jar               # or: java -jar jahaziel.jar --port=11451 --data-dir=/var/lib/jahaziel/data
~~~

`capabilities` lists the tools available on this host; health is served at `http://127.0.0.1:11451/actuator/health`. Override the port with `--port=N` or `PORT` in `.env`, and the data root with `--data-dir=PATH` (default: `data/` beside the jar). Real environment variables win over `.env`, and configuration is read at startup, so a credential change needs a restart.

### Path 2 — Run under an OS supervisor

Register the jar with the host's supervisor. The service definition stays where the OS requires it, never inside the jar directory:

| OS | Definition | Files to copy |
|---|---|---|
| Linux (systemd) | `/etc/systemd/system/jahaziel.service` | `ops/services/jahaziel.service` |
| macOS (launchd) | `/Library/LaunchDaemons/dev.jahaziel.plist` | `ops/launchd/dev.jahaziel.plist.template` + `ops/launchd/install.sh` |
| Windows (WinSW) | Windows Services entry `jahaziel` | `ops/windows/jahaziel-service.xml.template` + `ops/windows/install.ps1` |

~~~bash
# Linux (systemd)
sudo install -o root -g root -m 0644 ops/services/jahaziel.service /etc/systemd/system/jahaziel.service
sudo systemctl daemon-reload
sudo systemctl enable --now jahaziel
sudo systemctl status jahaziel --no-pager
sudo journalctl -u jahaziel -f          # logs
sudo systemctl restart jahaziel
sudo systemctl stop jahaziel
~~~

~~~bash
# macOS (launchd); install.sh substitutes the jar dir into the plist
sudo sh ops/launchd/install.sh /var/lib/jahaziel dev.jahaziel
launchctl print system/dev.jahaziel     # status
tail -f /var/lib/jahaziel/data/logs/launchd.out.log
launchctl kickstart -k system/dev.jahaziel   # restart
launchctl bootout system/dev.jahaziel        # stop
~~~

~~~powershell
# Windows (WinSW.exe is the binary you supply)
.\ops\windows\install.ps1 -JarDir C:\jahaziel -WinSw C:\tools\WinSW.exe
Get-Service -Name jahaziel                     # status
Get-Content C:\jahaziel\data\logs\*.log -Wait # logs
Restart-Service -Name jahaziel
Stop-Service -Name jahaziel
~~~

For HTTPS and workspace previews, copy the matching file from `ops/caddy/` (`Caddyfile.linux`, `Caddyfile.macos`, or `Caddyfile.windows`) into the host's Caddy config and replace the example hostnames. See [service templates per OS](docs/deployment/single-server.md#service-templates-per-os) for the full procedure.

### Path 3 — Clone the repository on the target

Prefer this on an air-gapped host, when you must audit or patch the source, or when a binary cannot be moved from a build machine. Prerequisites must already exist on the machine: JDK 25, Node.js 24 or newer, and pnpm 12.14.0. The build uses those existing toolchains and installs nothing globally.

~~~bash
git clone https://github.com/richie-rich90454/jahaziel.git
cd jahaziel
git checkout <release-tag-or-commit>
pnpm --dir web install --frozen-lockfile && pnpm --dir web build
./mvnw -B package -DskipTests -Dmaven.test.skip=true
sudo install --owner=jahaziel --group=jahaziel --mode=0640 target/jahaziel.jar /var/lib/jahaziel/jahaziel.jar
~~~

On Windows use `.\mvnw.cmd` and `Copy-Item`, or the repository's `dev.ps1` verbs (`bootstrap`, `build`), which keep Maven and pnpm caches in the repo-local, ignored `.cache/` directory. With raw commands, set `PNPM_CONFIG_STORE_DIR`, `PNPM_CONFIG_CACHE_DIR`, and `MAVEN_OPTS=-Dmaven.repo.local=<repo>/.cache/maven-repo` to keep caches repo-local. Then `init`, `doctor`, and register the supervisor as in paths 1 and 2.

### Local demo (development only)

~~~bash
java -jar jahaziel.jar --demo
~~~

Demo mode stubs the identity provider, seeds a sample user and workspace, and binds only to loopback. **Never expose it to a network or use it with real data.** See [demo mode](docs/guide/demo-mode.md).

### Updating an existing install

Use the staged updater instead of overwriting a running jar:

~~~bash
java -jar jahaziel.jar backup                                   # before anything else
java -jar jahaziel.jar support-bundle                           # redacted diagnostics
java -jar jahaziel.jar update --to ./jahaziel-NEW.jar --dry-run
java -jar jahaziel.jar update --to ./jahaziel-NEW.jar --sums ./SHA256SUMS
# stop the service, restart it, then:
java -jar jahaziel.jar doctor
curl --fail http://127.0.0.1:11451/actuator/health
~~~

`update` stages the candidate under `<jar>/data/update/<version>/`, verifies `SHA256SUMS`, and swaps the jar on the **next start**, keeping the previous jar for `update --rollback`. Stop the service first — especially on Windows, where a running process keeps the jar locked. The full procedure, including rollback and preserved state, is in [Updating and rollback](docs/deployment/single-server.md#updating-and-rollback).

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

`basis-auth` is one login plugin among several (see [Authentication providers](#authentication-providers)). Register a confidential client with a compatible DevConnect/Basis OIDC identity provider. Register these exact URLs in the provider:

- redirect URI: `https://code.example.com/api/auth/basis/callback`
- backchannel receiver: `https://code.example.com/api/auth/basis/backchannel`

Set the canonical lean key set in `.env`:

~~~dotenv
BASIS_AUTH_ISSUER=https://idp.example.com
BASIS_AUTH_CLIENT_ID=your-client-id
BASIS_AUTH_CLIENT_SECRET=your-client-secret
BASIS_AUTH_RESOURCE=urn:basis:api:jahaziel
BASIS_AUTH_RESOURCE_SECRET=your-resource-secret
BASIS_AUTH_INTERNAL_URL=http://127.0.0.1:3001
BASIS_AUTH_COOKIE_SECRET=<at-least-32-random-bytes>
CURRENT_URL_ORIGIN=https://code.example.com
PORT=11451
JAHAZIEL_LOG_LEVEL=INFO
JAHAZIEL_BACKEND=local

# Optional: persist share-link signing across restarts.
# JAHAZIEL_SHARE_SECRET=<long-random-secret>
~~~

`CURRENT_URL_ORIGIN` is the public HTTPS origin with no trailing path; it derives both registered URLs above. `BASIS_AUTH_RESOURCE_SECRET` verifies backchannel webhook proofs, and `BASIS_AUTH_INTERNAL_URL` is the private listener for webhook verification and fail-closed user-state checks. `BASIS_AUTH_COOKIE_SECRET` is at least 32 random bytes; this app keeps sessions server-side and signs tokens with generated keys in `data/keys`, so the value is accepted but not used to encrypt cookies. Login stays off until the issuer, client id, client secret, resource, and origin are configured. Keep `.env` private (`chmod 0600`). Configuration is read at startup, so a credential change requires a restart.

Legacy `BASIS_REDIRECT_URI`, `BASIS_CLIENT_ID`, `BASIS_CLIENT_SECRET` and `BASIS_RESOURCE` remain accepted as backward-compatible aliases; use one spelling consistently. For optional Microsoft Entra login the keys are `MICROSOFT_TENANT_ID`, `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, and `MICROSOFT_WEBHOOK_STATE` / `MICROSOFT_WEBHOOK_ID`; this build reads `MICROSOFT_ISSUER` (tenant in the URL), `MICROSOFT_CLIENT_ID`, and `MICROSOFT_CLIENT_SECRET` from `.env`. Grant administrator rights through your identity provider or database — there is no `.env` admin-bootstrap key in this build. For the full downstream IdP contract and secret-rotation procedure, see the [basis-auth integration guide](docs/basis_auth_setup_guide.md). To use Google, GitHub, generic OIDC, local password + TOTP, passkey, API token, or LDAP instead, see [Authentication providers](#authentication-providers).

### Operations (CLI verbs)

~~~bash
java -jar jahaziel.jar doctor                    # preflight
java -jar jahaziel.jar capabilities              # tool capabilities (unavailable with --offline)
java -jar jahaziel.jar backup                     # back up .env + data/ (--no-secrets to omit secrets)
java -jar jahaziel.jar restore <file>             # restore a backup
java -jar jahaziel.jar support-bundle             # redacted diagnostics bundle
java -jar jahaziel.jar update --status            # staged/applied update state
java -jar jahaziel.jar update --to <new-jar>      # stage a verified jar update
java -jar jahaziel.jar update --rollback          # restore the pre-update jar
java -jar jahaziel.jar upgrade --to <new-jar>     # immediate offline swap, keeps a .bak
java -jar jahaziel.jar logs --follow              # application logs
~~~

See the [CLI reference](docs/reference/cli.md), [backup and restore](docs/operations/backup.md), [update and rollback](docs/deployment/single-server.md#updating-and-rollback), and [release acceptance checklist](docs/operations/acceptance.md).

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

A plugin is enabled only when it is configured; missing required settings do not silently create a login source. The canonical basis-auth keys are the lean set above; the legacy `BASIS_*` spellings remain backward-compatible aliases.

### Provider configuration keys

Each provider reads its own keys from jar-adjacent `.env`, real environment variables, or the external `application.yml`. Dotted names are the property/`.env` form; uppercase names are the environment form.

| Provider | Required keys | Optional keys |
|---|---|---|
| `basis-auth` | `BASIS_AUTH_ISSUER`, `BASIS_AUTH_CLIENT_ID`, `BASIS_AUTH_CLIENT_SECRET`, `BASIS_AUTH_RESOURCE`, `CURRENT_URL_ORIGIN` | `BASIS_AUTH_RESOURCE_SECRET`, `BASIS_AUTH_INTERNAL_URL`, `BASIS_AUTH_COOKIE_SECRET`; legacy `BASIS_REDIRECT_URI` / `BASIS_CLIENT_ID` / `BASIS_CLIENT_SECRET` / `BASIS_RESOURCE` aliases |
| `microsoft`, `google`, `oidc` | `<prefix>.client-id` (`MICROSOFT_CLIENT_ID`, `GOOGLE_CLIENT_ID`, `OIDC_CLIENT_ID`) | `<prefix>.client-secret`, `<prefix>.redirect-uri`, `<prefix>.issuer`, `<prefix>.scopes` |
| `github` | `github.client-id` / `GITHUB_CLIENT_ID` | `github.client-secret`, `github.scopes`, `github.authorize-url`, `github.token-url`, `github.user-url`, `github.emails-url` |
| `local` | `local.username`, `local.password` | `local.email` (admin is granted only after TOTP enrollment) |
| `passkey` | `passkey.origin`, `passkey.rp-id` | `passkey.rp-name` |
| `api-token` | — | `api-token.ttl-millis`, `api-token.issuer` |
| `ldap` | `ldap.url`, `ldap.base-dn` | `ldap.subject-attribute`, `ldap.email-attribute`, `ldap.display-name-attribute`, `ldap.mfa-attribute`, `ldap.mfa-value`, `ldap.user-filter`, `ldap.bind-dn`, `ldap.bind-password` |

Identity linking uses the pair **(provider, subject)**: `AuthIdentity.linkKey()` is `provider + ":" + subject`. Email is profile data only and is never used alone to link accounts, so two providers may assert the same address without colliding. See the [configuration guide](docs/guide/configuration.md#authentication-providers).

## Using Jahaziel

### From Windows

1. Run the server on Linux, macOS, or Windows using Path 1, 2, or 3 above (or the local demo for development only).
2. In Windows, open Edge, Chrome, or Firefox and go to `https://code.example.com` (or `http://<host>:11451` for a private direct run on the same host).
3. Sign in through your configured provider. The browser is the only thing you need installed to use a remote server.
4. Use terminal, tasks, and debug panels exactly as on iPad; keyboard/trackpad shortcuts work in desktop browsers.

To develop, rebuild, and host the jar on Windows, use the PowerShell workflow in [Path 3 — Clone the repository on the target](#path-3--clone-the-repository-on-the-target), the `dev.ps1` verbs (`build`, `test`, `run`, `dev`, `smoke`, `docs`, `cache:report`, `clean`), and the WinSW service template in [service templates per OS](docs/deployment/single-server.md#service-templates-per-os).

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

These modules are present in the jar alongside the core workbench (files, editor, terminal, run, preview, Git, debug, and languages). Most have a dedicated SolidJS service panel; modules marked **backend-only** ship their domain/service layer without one.

| Module | Package | Status |
|---|---|---|
| Collaboration — members, invitations, file comments, share links, live sessions, audit | `dev.jahaziel.collaboration` | SolidJS panel available; JDBC store included |
| Agent assistant — chat, sessions, patch review, prompts, model catalog | `dev.jahaziel.agent` | SolidJS panel available |
| Agent tools — managed coding-tool service seam | `dev.jahaziel.agenttools` | Backend-only; OpenCode is launched from the terminal |
| Data tools — CSV/SQLite browsing, notebook cells, file diffs, search index | `dev.jahaziel.datatools` | SolidJS panel available |
| Club — teaching sessions, roles, validation tasks, checkpoints | `dev.jahaziel.club` | SolidJS panel available; additive and optional, Git remains the source of truth |
| Operations — support bundles, backups, approvals, GDPR requests | `dev.jahaziel.ops` | SolidJS panel available (Admin ops) |
| Observability — metrics, hourly rollups, resource sampling | `dev.jahaziel.monitoring` | Backend-only; health endpoint is available |
| Compliance — audit records, retention, policy evaluation, administration actions | `dev.jahaziel.audit`, `dev.jahaziel.policy`, `dev.jahaziel.administration` | Backend-only; administrative actions are exposed through the Admin ops panel |

## Maintainability

- **Plugin seams.** Host, provider, and port-discovery behavior lives behind small contracts (`dev.jahaziel.plugin`, `dev.jahaziel.identity.auth`, `dev.jahaziel.platform.spi`, and the per-OS port discovery plugins) instead of OS/provider branches in feature code.
- **AP CSA-level Java.** New and touched application code favors plain classes, abstract classes, and constructs teachable in AP Computer Science A; a few older classes still use newer syntax. The build still uses JDK 25 and targets Java 25 bytecode.
- **4-space indentation** in Java and in the SolidJS + DaisyUI TypeScript frontend; TypeScript is formatted with `oxfmt`.
- **Small, focused commits.** Prefer single-file commits where the change allows; each commit carries one cohesive behavior with its docs and focused verification.

## Documentation

- [Deploy Jahaziel on a single server](docs/deployment/single-server.md) — jar upload, systemd/launchd/WinSW, repo clone, updates and rollback
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

Jahaziel is under active development. The project version is `0.1.0-SNAPSHOT`; workflow artifacts are build outputs for evaluation, not stable release packages. The Linux x64 and arm64 CI pipeline — including the packaged browser smoke and a 62-test Playwright E2E suite that checks the SolidJS workbench against a local stub — is green. The Windows job only lists the E2E suite's tests (`playwright test --list`); it does not run them. Those are regression checks, not host acceptance, and the project is **not release-accepted**: a release still requires real-device iPad testing, native acceptance on both Linux architectures, and real-host checks on macOS and Windows with their service templates. Review the [current plan](docs/plan.md) and [release acceptance checklist](docs/operations/acceptance.md) before deploying it for important work.

## Contribute and get help

Bug reports and feature proposals are welcome. Include the server architecture, Java version, reproduction steps, and relevant redacted logs. Never attach `.env`, private keys, workspace source, or unredacted support bundles.

- [Report a bug](https://github.com/richie-rich90454/jahaziel/issues/new?template=bug_report.md)
- [Request a feature](https://github.com/richie-rich90454/jahaziel/issues/new?template=feature_request.md)
- [Read the contribution guide](CONTRIBUTING.md)

If Jahaziel fits your workflow, star the repository and tell us what you use it for.

## License

Apache License 2.0. See [LICENSE](LICENSE).
