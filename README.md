<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel

[![Linux build](https://github.com/richie-rich90454/jahaziel/actions/workflows/linux.yml/badge.svg?branch=main)](https://github.com/richie-rich90454/jahaziel/actions/workflows/linux.yml)
[![Apache-2.0 license](https://img.shields.io/github/license/richie-rich90454/jahaziel)](LICENSE)
[![Linux x64 and arm64](https://img.shields.io/badge/server-Linux%20x64%20%2B%20arm64-2f6f62)](docs/guide/platforms.md)
[![Java 25+](https://img.shields.io/badge/Java-25%2B-2f6f62)](docs/deployment/single-server.md)

**A lightweight, self-hosted browser IDE for source code stored on a Linux server.**

Jahaziel gives trusted users persistent workspaces with a browser editor, terminal, run tasks, previews, and optional language diagnostics. The server is one Java application; developers connect from a modern browser without needing a local IDE or SSH for normal editor sessions.

> [!WARNING]
> Jahaziel runs workspace terminals, tasks, and Git operations as native Linux processes under the same service account. Use it with trusted users only. It does not provide operating-system isolation between workspaces or hostile multi-tenant security.

## Why Jahaziel

- **Edit server-side source in the browser.** Browse workspace files, edit with CodeMirror 6, and detect conflicting saves.
- **Use a real Linux shell.** Open an authenticated xterm.js terminal when needed; terminal processes run in the workspace directory.
- **Run and preview projects.** Start template tasks, inspect output, and open workspace-scoped preview links.
- **Start from a useful template.** Create separate workspace homes from Node.js, Python, Java, C/C++, Go, Rust, static-site, or empty starters.
- **Add language diagnostics when needed.** Select pinned language packs explicitly; Jahaziel caches them on the server and does not fetch them at startup.
- **Keep source control close to the editor.** Review Git status and diffs, stage or unstage changes, and commit in an existing repository.
- **Keep operations small.** Deploy one jar behind one HTTPS reverse proxy. Workspace files, SQLite state, keys, logs, and optional language packs live under the data directory.

## Try a local demo

The Linux build workflow publishes architecture-specific JAR artifacts. Download `jahaziel-linux-x64` or `jahaziel-linux-arm64` from a successful [Linux build](https://github.com/richie-rich90454/jahaziel/actions/workflows/linux.yml), then run it on a matching Linux host with Java 25 or newer:

~~~bash
java -version
java -jar jahaziel.jar init
java -jar jahaziel.jar --demo
~~~

Open [http://127.0.0.1:11451](http://127.0.0.1:11451). Demo mode seeds a sample user and workspace, skips identity-provider setup, and binds to loopback. Keep it local; do not expose demo mode to a network or use it with sensitive data.

For a persistent server with real sign-in and HTTPS, follow the [Linux deployment guide](docs/deployment/single-server.md).

## What runs where

| Component | Supported target |
|---|---|
| Jahaziel server | Linux x86_64 or arm64 |
| Browser workbench | Modern browsers on Windows, macOS, and Linux |
| Workbench editor and terminal | CodeMirror 6 and xterm.js, served from the Jahaziel jar |
| Workspace execution | Native Linux processes under the Jahaziel service account |

The jar does not bundle a VS Code server or project runtimes. Install only the host runtimes needed for the projects users choose to run. Jahaziel never prefetches language packs; a language pack is downloaded only after someone explicitly selects it, and some packs include a pinned helper runtime.

## Build from source

The maintained development workflow uses native PowerShell on Windows. It checks existing tools and keeps Maven and pnpm caches inside ignored repository-local directories.

Requirements: an existing JDK 25, Node.js 24 or newer, Maven, and pnpm 12.14.0. The scripts do not install runtimes or change the system PATH.

~~~powershell
git clone https://github.com/richie-rich90454/jahaziel.git
cd jahaziel
.\dev.ps1 bootstrap
.\dev.ps1 build
~~~

The packaged application is written to `target/jahaziel.jar`. See [contributing](CONTRIBUTING.md) for the local workflow and [Linux CI](.github/workflows/linux.yml) for native x64 and arm64 build, verification, and packaged-browser smoke flows.

## Documentation

- [Deploy Jahaziel on Linux](docs/deployment/single-server.md)
- [Quick start](docs/guide/quick-start.md)
- [Use the workbench](docs/guide/using-jahaziel.md)
- [Configure authentication and server settings](docs/guide/configuration.md)
- [Workspace templates](docs/guide/templates.md)
- [Back up and restore data](docs/operations/backup.md)
- [Architecture](docs/architecture/overview.md)
- [Security and trust boundary](docs/security/threat-model.md)
- [CLI reference](docs/reference/cli.md)
- [Current product plan](docs/plan.md)

## Project status

Jahaziel is under active development. The project version is `0.1.0-SNAPSHOT`; workflow artifacts are build outputs for evaluation, not stable release packages. Review the [current plan](docs/plan.md) and [release acceptance checklist](docs/operations/acceptance.md) before deploying it for important work.

## Contribute and get help

Bug reports and feature proposals are welcome. Include the server architecture, Java version, reproduction steps, and relevant redacted logs. Never attach `.env`, private keys, workspace source, or unredacted support bundles.

- [Report a bug](https://github.com/richie-rich90454/jahaziel/issues/new?template=bug_report.md)
- [Request a feature](https://github.com/richie-rich90454/jahaziel/issues/new?template=feature_request.md)
- [Read the contribution guide](CONTRIBUTING.md)

If Jahaziel fits your workflow, star the repository and tell us what you use it for.

## License

Apache License 2.0. See [LICENSE](LICENSE).
