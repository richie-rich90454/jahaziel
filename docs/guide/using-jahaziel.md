<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Using Jahaziel

The browser workbench at `http://localhost:11451` is served by the Linux
Jahaziel jar. It manages source files stored on the server and opens terminal
sessions only when you request one.

## Create and edit

1. Sign in, enter a workspace name, and choose a server-managed starter.
2. Jahaziel creates a private home directory for the new workspace and writes
the template starter files there.
3. Open the workspace. Use the explorer to browse folders, create/rename/delete
files, and select a text file in the editor.
4. Modified tabs show an unsaved marker. Save sends the file's latest ETag. If
another process has changed it, the save is rejected so newer server content
is not silently overwritten. Copy your local edits before reloading.
5. Open a terminal explicitly to run commands in that workspace home. Closing
the terminal stops its shell process.
6. Use template task controls when available. Preview links are workspace
scoped and open only registered ports. The workbench shows whether each
registered port accepts connections; use **Check ports** after starting a
server from the terminal.
7. For an existing Git repository, use Source Control to inspect changes,
stage or unstage files, view diffs, and commit staged changes. Configure a Git
author identity in the repository before the first commit. Workbench commands
use a sanitized process environment and skip repository hooks. Clone
repositories, configure credentials, and run hooks from the workspace
terminal.

## OpenCode on iPad and desktop

OpenCode is the only coding-agent CLI in the current catalog. When you first
open a workspace, Jahaziel starts installing the pinned Linux binary and its
search helper into your per-user data area. The terminal panel shows download
progress and the installed version. Once ready, choose **Open OpenCode** to
launch it in an authenticated terminal rooted at the active workspace.

Sign in to an AI provider from the OpenCode TUI using your own provider
account. Jahaziel does not collect or share provider credentials. Your
OpenCode profile is kept under `data/users/<your-user-id>/profile/` and is
available from your other workspaces on this Jahaziel server. Updating
reinstalls the catalog's pinned version; removing OpenCode deletes its managed
executables but keeps your profile and provider sign-in. Claude Code is not
available in the current product catalog.

On iPad, use the terminal key strip for keys such as Ctrl, Esc, Tab, and the
arrow keys. Terminal clipboard output is offered as a user-confirmed copy
action so the page does not silently replace your clipboard.

## Language diagnostics

The Language panel lists pinned server-managed tools that can be enabled for a
workspace. Enabling a tool downloads its verified release into the shared
`data/language-packs/` cache; Jahaziel does not fetch tools during startup or a
normal build. Rust Analyzer, the Java language server, TypeScript, Pyright, and
gopls are available on Linux x64 and arm64. clangd is available on Linux x64.
The Go pack also downloads a pinned Go SDK when selected. Open a matching
source file after enabling its pack to start diagnostics. Disabling a pack
removes the workspace selection while keeping the shared cached release
available for reuse or pruning.

## Debug a program

The Debug panel can launch a program through a configured DAP adapter for
Node.js, Python, Java, C/C++, Go, or Rust. A server administrator must first
install and pin a compatible adapter and add its fixed absolute command to
`jahaziel.debug.adapters`; Jahaziel does not install debugger adapters. See
[debug adapter configuration](./configuration.md#debug-adapters). If no
adapter is configured for a language, starting a session reports that the
adapter is unavailable.

Start a workspace, then enter a workspace-relative program path (or Java
main class), working directory, and JSON argument array. To use a supported
`.vscode/launch.json` entry, select **Load .vscode/launch.json**, then choose a
configuration; its fields populate the launch form. Unsupported fields and
attach configurations are rejected. **Start debugging** opens a
workspace-scoped session. Use **Set** to send comma-separated line numbers
for a workspace-relative file, then use Continue, Pause, and the step
controls. Select a call-stack frame to inspect its variables; add watch
expressions or evaluate an expression in the debug console. If the browser
disconnects while the adapter remains active, use **Reconnect** to resume the
DAP stream. Stop the session when finished.

This is a launch-only debugger slice. It does not attach to existing
processes, launch external terminals, install or update adapters, or provide
editor-gutter breakpoints. Adapters run as the Jahaziel service account, with
the same trusted-user boundary as terminals and tasks. See the
[supported launch configuration subset](./configuration.md#debug-adapters).

## Workspace states

`requested`, `preparing`, `booting`, `ready`, `active`, `idle`, `suspended`,
and `failed` describe the existing workspace lifecycle. A failed start keeps
the workspace home. Read the visible reason and operation reference, correct
the problem, then retry. Include the operation reference when looking for
matching server log entries. Setup and task panels also display references for
their individual attempts.

## Process trust boundary

The first release runs terminal and task processes as the same Linux account
as Jahaziel. Use it with trusted users only. Workspace file API checks do not
sandbox a native shell from other files accessible to that account. Git
operations also run as this account. Workbench Git commands use a sanitized
environment and do not execute repository hooks; terminal commands remain
inside the same trusted-user process boundary.

## Troubleshooting

| Symptom | What to check |
|---|---|
| Sign in is unavailable | Complete the configured identity-provider values in `.env`; use demo mode only on localhost |
| Workspace files are empty | Check template apply status and the server data directory permissions |
| Save reports a conflict | Another terminal process changed the file; copy your edits and reload before retrying |
| Terminal cannot connect | Check workspace write permission, terminal session limits, server logs, and `/bin/bash` or `/bin/sh` |
| Preview is unavailable | Confirm the workspace task is running, its port is registered, and the gateway route is authorized |
| Doctor reports a storage failure | Check the paths and per-directory byte totals printed by `doctor` |
| Server startup reports a workspace-home migration conflict | Keep both directories intact, resolve the conflicting file under the workspace home, then restart; the migration will not overwrite either copy |

See [quick start](./quick-start.md), [configuration](./configuration.md),
and the [trusted-process threat model](../security/threat-model.md).
