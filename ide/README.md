<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel Code

Self-hosted web IDE forked from the VS Code stable channel. One pinned upstream tag plus a
numbered patch stack; no forks of forks.

- `upstream-version.txt` pins the upstream tag.
- `patches/` holds the ordered patch stack, one coherent change per patch.
- `build.sh` checks out the tag, applies the stack, builds the REH for the
  host variant, and packs it with a `.sha256` sidecar.
- `sign.sh` checksums and signs release artifacts.
- `branding/` holds icons, fonts, and theme tokens.
- `scripts/rebrand.py` generates the string-replacement patch from a mapping table.
- `tests/` holds smoke, patch-regression, security, performance, compatibility, and browser tests.

See `docs/ide/patch-manifest.md` for what each patch does, its current
rebase state, and how it is verified. See
`docs/ide/building-the-ide-server.md` for prerequisites, the artifact
contract, and install steps.

## Prerequisites

- Bash plus Node 24.18 or newer, `npm`, and `zstd` for `build.sh` (Git Bash
  or MSYS2 on Windows; macOS falls back to `shasum` automatically).
- A C++ toolchain for upstream native modules. On Windows, Visual Studio
  must include the Spectre-mitigated libraries component, or `npm ci`
  fails with `MSB8040`.
- The Ubuntu server provisioner `ops/toolchain/install-toolchain.sh` covers
  the full server topology only; the fat jar needs just Java 25.
- The upstream checkout lives in the gitignored `upstream/` directory; run
  `build.sh` to clone the pinned tag and apply the stack.

## REH variants

Six targets: `linux-x64`, `linux-arm64`, `darwin-x64`, `darwin-arm64`,
`win32-x64`, `win32-arm64`. `IdeService` selects the variant from
`jahaziel.reh` (or `-Djahaziel.reh=<variant>`), defaulting to the host OS
and architecture, then extracts once to `data/ide/<variant>/` guarded by a
`.extracted-<version>-<variant>` sentinel. Tarballs are SHA-256 verified
against their `.sha256` sidecar before extraction; a missing sidecar fails
closed. The gateway relays both plain HTTP and WebSocket upgrades to the
extracted server's port.

`build.sh` builds only the variant of the host it runs on, because
upstream compiles native modules per platform and cannot cross-compile
them. It emits `out/jahaziel-code-server-<variant>.tar.zst` and a matching
`.sha256` sidecar, ready to copy into `data/ide/`.

