<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel Code

Self-hosted web IDE forked from the VS Code stable channel. One pinned upstream tag plus a
numbered patch stack; no forks of forks.

- `upstream-version.txt` pins the upstream tag.
- `patches/` holds the ordered patch stack, one coherent change per patch.
- `build.sh` checks out the tag, applies the stack, and builds the web client and the server.
- `sign.sh` checksums and signs release artifacts.
- `branding/` holds icons, fonts, and theme tokens.
- `scripts/rebrand.py` generates the string-replacement patch from a mapping table.
- `tests/` holds smoke, patch-regression, security, performance, compatibility, and browser tests.

See `docs/ide/patch-manifest.md` for what each patch does and how it is verified.
