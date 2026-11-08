<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Ports

| Port | Default | Meaning |
|---|---|---|
| API/workbench | `11451` (`PORT`, `--port`) | Same-origin UI, API, admin page, health endpoint, terminal WebSockets, and preview gateway |
| Standalone gateway | off (`--gateway-port`, default `8081`) | Optional listener for workspace HTTP and WebSocket previews |

The workbench editor is served from the application port. It does not start
or proxy to a per-workspace IDE server. Workspace preview URLs route to
registered ports and require workspace-scoped authorization. Configure the
public domain with `JAHAZIEL_DOMAIN`; use `localhost` for local browser
access where the wildcard-host routing is supported.