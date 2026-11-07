<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Workspace templates

Last verified against version 0.1.0-SNAPSHOT.

Pick a template when creating a workspace. The jar seeds its templates into
`data/templates/` on first run; user edits win on later runs. Applying a
template creates a workspace-specific home and materializes its starter files.
Templates do not install language runtimes or dependencies during server startup.

| Template | Contents |
|---|---|
| `empty` | Bare workspace, shell and editor |
| `node` | Node.js preview server on port 3000 and a runnable TypeScript example |
| `python` | Minimal Python starter and port 8000 |
| `java` | Java starter files |
| `cpp` | C++ starter files |
| `go` | Go starter files |
| `rust` | Rust starter files |
| `static-site` | Static HTML starter and port 8080 |

Each template is a server-managed folder with a `template.json` (`name`,
`description`, `vcpuCount`, `memoryMib`, `ports`, `languagePacks`, and
`starterFiles`). Paths and resource bounds are validated before workspace
creation. A template may declare at most 32 unique ports. Starter content is
capped at 256 files, 256 KiB per file, and 2 MiB in total. Only administrators
who can edit the server's template directory
can change starter content. List and apply via `GET /api/v1/templates` and
`POST /api/v1/templates/{name}/apply`.

`languagePacks` lists the diagnostics packs relevant to a template. The
workbench marks other catalog entries as unsupported and prevents enabling
them for that workspace. An omitted or empty list leaves the server catalog
available. Applying a template never downloads a language pack; the workspace
owner explicitly enables one in the workbench.

The Node template's TypeScript example runs with Node.js 22.18.0 or newer and
uses only erasable TypeScript syntax. Node strips those types but does not
type-check them at runtime; the optional TypeScript language pack provides
editor diagnostics and requires Node.js 22.22.2 or newer. Language packs
download only after you select one. The template provides two alternative run
tasks: the preview server and the standalone TypeScript example. Stop one task
before starting the other.
