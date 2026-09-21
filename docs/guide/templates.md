<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Workspace templates

Last verified against version 0.1.0-SNAPSHOT.

Pick a template when creating a workspace. The jar ships five seeds into
`data/templates/` on first run; user edits always win on later runs.

| Template | Contents |
|---|---|
| `empty` | Bare workspace, shell and editor |
| `node` | Node.js 24, `npm install` on first start |
| `python` | Python workspace |
| `java` | Java workspace |
| `static-site` | Static site skeleton |

Each template is a folder with a `template.json` (`name`, `description`,
`vcpuCount`, `memoryMib`, `ports`, `setup`). Drop your own folder beside
them. List and apply via `GET /api/v1/templates` and
`POST /api/v1/templates/{name}/apply`.
