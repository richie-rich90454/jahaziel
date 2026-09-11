<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Toolchain layers

Versioned toolchain overlays copied into the base image by `build.sh`.
Each directory holds one toolchain with its exact version pinned.

| Directory | Contents |
|---|---|
| `cpp/` | gcc, g++, clang, clang++, cmake, make, ninja, gdb, lldb |
| `java/` | JDK 25, maven, gradle |
| `python/` | python3, pip, venv, uv |
| `node/` | Node.js LTS, npm, pnpm, bun, typescript |
| `go/` | go toolchain |
| `rust/` | rustc, cargo |

Never mutate a live toolchain. Ship a new base image, roll it out
canary-first, keep the old image for rollback.
