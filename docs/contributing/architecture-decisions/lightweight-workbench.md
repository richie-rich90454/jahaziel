<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Lightweight browser workbench

- Status: accepted
- Date: 2026-10-28
- Supersedes: the IDE implementation choice in `ide-server-base.md`

Jahaziel serves a same-origin workbench from its executable jar. A full
VS Code-derived server and extension host are too large for the first
release's remote-source editing workflow. The browser workbench uses
CodeMirror 6 for text editing and xterm.js for terminal display, while the
server keeps workspace files and process metadata under its configured data
root and runs requested processes in each workspace home.

The following direct dependencies are approved for this design:

- **CodeMirror 6** and its HTML, JavaScript, JSON, and Python language
  packages provide browser editing and syntax support without a bundled
  server-side IDE or compiler.
- **xterm.js** and its fit addon render interactive terminal output; the
  server-side **pty4j** dependency owns the Linux pseudo-terminal.
- **Spring WebSocket** carries authenticated terminal and language-service
  streams through the existing application origin and port.
- **Vite** builds the maintained TypeScript sources in `web/src` into the
  static assets packaged in the jar. **TypeScript** checks those sources.
- The browser smoke uses Playwright only in its isolated CI tool directory;
  it is not an application or release dependency.

Versions remain pinned in `pom.xml`, `web/package.json`, and
`web/pnpm-lock.yaml`. Language servers and their runtimes are optional,
explicitly selected, checksum-verified assets under the server data root;
they are not built into the jar. Any additional direct application
dependency or editor runtime requires a separate decision and a measured
need.
