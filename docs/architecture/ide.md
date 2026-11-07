<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Browser workbench

Jahaziel serves one lightweight, same-origin browser workbench from its
single jar. `web/src` is the maintained TypeScript source. Vite builds it
into static assets; Maven packages those assets under `static/`.

CodeMirror 6 provides text editing and language modes. xterm.js displays a
terminal stream backed by an explicitly requested server-side PTY. The
server does not include the VS Code fork, REH binaries, language servers,
or compiler toolchains in the jar.

The first usable workflow is a workspace chooser, a server-managed template
picker, file tree and bounded quick-open search, editor tabs with ETag save
conflicts, a terminal panel, run tasks, workspace preview links, and a compact
Git panel for status, diff, stage, unstage, and commit. Git panel commits use
repository Git identity. The panel clears inherited environment secrets and
disables repository hooks; cloning, credential setup, and hook execution stay
in the terminal.
Optional, explicitly selected Rust, C/C++, Java, Go, TypeScript, JavaScript,
and Python language services run from pinned shared server-side packs. The Go
pack downloads the official Go 1.27.3 SDK for the server architecture and builds
gopls v0.24.0 only when selected. Go module downloads are checksum-verified by
the Go module system; the temporary compiler and module caches are removed when
installation finishes. Each workspace keeps its own Go module and build caches
under application data so projects cannot share mutable module state. TypeScript
and Python packs use the configured existing Node.js runtime. VS Code extension
compatibility, a plugin marketplace, debugger parity, and collaborative editing
are deferred.

The first release offers clangd diagnostics on Linux x64 only. Arm64 release
acceptance uses an available cross-architecture pack such as Pyright; C/C++
diagnostics on arm64 are deferred until Jahaziel can provide a small,
app-managed artifact without pulling in the full LLVM distribution or relying
on a distribution-specific system package. The upstream standalone clangd
downloads currently document Linux x86-64, while the official LLVM release
offers a full Linux Arm64 toolchain archive ([clangd downloads](https://clangd.llvm.org/installation),
[LLVM releases](https://github.com/llvm/llvm-project/releases)).

The server targets Linux x64 and arm64. Windows and macOS are browser
clients. Runtime processes use the Jahaziel service account, so terminal and
task access is for trusted users only.

The former VS Code OSS server decision is historical and superseded by the
[lightweight workbench contract](../plan.md).
