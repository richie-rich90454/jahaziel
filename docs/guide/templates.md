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
| `python` | Minimal Python starter, Python example, and port 8000 |
| `java` | Java source with a compile-and-run task |
| `cpp` | C++ source with a compile-and-run task |
| `go` | Go source with run and test tasks |
| `rust` | Rust source with run and test tasks |
| `static-site` | Static HTML starter and a Python preview task on port 8080 |

Each template is a server-managed folder with a `template.json` (`name`,
`description`, `vcpuCount`, `memoryMib`, `ports`, `languagePacks`, and
`starterFiles`, and optional `runTasks`). Paths and resource bounds are validated before workspace
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

The task picker combines tasks declared by the server-managed template with
tasks discovered from project metadata. At the workspace root, it recognizes
the common `dev`, `start`, `preview`, `serve`, `test`, and `build` scripts in
`package.json`; it selects pnpm, npm, or Yarn from the `packageManager` field
and lockfiles. The Node starter declares pnpm 12.14.0 and needs that version
installed on the Linux server before its package scripts can run. No package
manager, dependency, runtime, or language pack is installed automatically.
The picker also offers bounded common commands for Make, Maven, Gradle, Cargo,
and Go project markers. These commands execute project build files under the
Jahaziel service account and are intended for trusted workspaces.

Tasks with a declared preview port receive `PORT` and
`JAHZIEL_PREVIEW_BASE_PATH`. Preview links use the fixed host and route
`/{workspaceId}/{port}/`; included starters work with that route. A project
that generates root-relative asset URLs may need its own base-path setting.

The Node template's TypeScript example runs with Node.js 22.18.0 or newer and
uses only erasable TypeScript syntax. Node strips those types but does not
type-check them at runtime; the optional TypeScript language pack provides
editor diagnostics and requires Node.js 22.22.2 or newer. Language packs
download only after you select one. The template provides two alternative run
tasks: the preview server and the standalone TypeScript example. Stop one task
before starting the other.
