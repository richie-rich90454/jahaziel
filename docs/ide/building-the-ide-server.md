<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Building the IDE server

One command builds the REH for the machine you run it on:

```bash
sh ide/build.sh
```

The result is a browser-served VS Code server, installed into the
runtime by copying two files into `data/ide/`.

## Prerequisites

| Requirement | Notes |
|---|---|
| Node.js 24.18.0 or newer | Pinned by `ide/upstream/.nvmrc`; `build/npm/preinstall.ts` aborts on older versions |
| `npm` | Ships with Node |
| `zstd` | Required to pack the tarball |
| `git` | Clones and patches the upstream checkout |
| A C++ toolchain | Compiles upstream native modules; on Windows this needs Visual Studio, but **not** the optional Spectre-mitigated libraries component — `ide/scripts/native-build.mjs` builds with `/p:SpectreMitigation=false`, which also fixes `MSB8040` and the missing `delayimp.lib` link error |

## What the script does

1. Verifies the toolchain and the Node version.
2. Detects the host variant (`win32-x64`, `linux-x64`, `darwin-arm64`, …).
   Each host builds only its own variant: upstream compiles native
   modules per platform and cannot cross-compile them.
3. Clones or resets `ide/upstream/` to the tag in `ide/upstream-version.txt`,
   deletes the stripped extensions (`copilot`, `media-preview`,
   `simple-browser`), and sets `core.whitespace=cr-at-eol` so LF patches
   apply to the CRLF checkout Git for Windows produces.
4. Applies `ide/patches/*.patch` in order, failing closed on any conflict.
5. Installs per-directory dependencies with `ide/scripts/install-deps.mjs`
   (upstream's postinstall cannot be used: it runs lifecycle scripts, so the
   first native module aborts it and leaves the rest uninstalled), compiles
   the native modules of all three dependency trees
   (`upstream`, `upstream/remote`, `upstream/build`) with
   `ide/scripts/native-build.mjs`, and runs
   `npm run gulp vscode-reh-web-<slug>-min`.
6. Packs the build as `ide/out/jahaziel-code-server-<slug>.tar.zst` plus a
   matching `.sha256` sidecar.

## Installing the artifact

```bash
cp ide/out/jahaziel-code-server-<slug>.tar.zst <jar-dir>/data/ide/
cp ide/out/jahaziel-code-server-<slug>.tar.zst.sha256 <jar-dir>/data/ide/
java -jar jahaziel.jar doctor   # reh must now report [ok]
```

The first boot extracts the tarball to `data/ide/<slug>/` and writes a
`.extracted-<version>-<slug>` sentinel, so later starts reuse it.

## Windows notes

- Run the script from Git Bash, MSYS2, or WSL: `sh ide/build.sh`.
- If `npm` fails with `No such file or directory`, a WSL `bash.exe` stub
  is shadowing the real shell. The script already prepends the running
  shell's directory to `PATH`; if you invoke the steps by hand, make
  sure `C:\msys64\usr\bin` (or your shell's `bin`) precedes
  `WindowsApps` on `PATH`.
- `doctor` expects the `win32` variant, because the local backend runs
  the IDE on the same host.

## Distributable fat jar

`mvn package -Pbundled-ide` embeds `ide/out/jahaziel-code-server-*.tar.zst`
(plus its `.sha256`) into the jar, so `init` stages it into `data/ide/`
and any directory becomes runnable with no extra download: drop the jar,
`init`, fill `.env`, `doctor`, serve. The profile fails closed when the
tarball is absent — run `sh ide/build.sh` first. Unit runs stay slim:
without the profile nothing is bundled and no test slows down.

Always build distributables with `clean`. IDE background compilers write
class files into `target/` that incremental Maven builds can repackage
unchecked; a stale class once shipped an unbootable jar.

Two packaging pitfalls are now enforced by the build itself, so they cannot
recur silently:

- A profile `<resources>` list *replaces* the Super POM default instead of
  merging. The `bundled-ide` profile therefore repeats `src/main/resources`
  explicitly; without that the jar ships with no web UI at all.
- `spring-boot-starter-parent` already binds `repackage`; do not add another
  `repackage` execution or the jar is repackaged twice. The profile also
  verifies the finished jar contains both `BOOT-INF/classes/static/index.html`
  and the REH tarball, failing the build otherwise.
