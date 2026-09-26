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
