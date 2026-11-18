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
or proxy to a per-workspace IDE server. In the packaged deployment, preview
URLs use the fixed host `preview.<JAHAZIEL_DOMAIN>` and the route
`/{workspaceId}/{port}/`. Register one DNS record for the IDE hostname and
one for the fixed preview hostname; wildcard DNS is not needed. Both hosts
terminate TLS at the same reverse proxy and route to port `11451`.

Preview requests are resolved to an active workspace port and require a
workspace and port scoped token. The preview hostname isolates project
content from the IDE origin, while all preview pages share that preview
origin. Local demo and standalone-gateway deployments may use their configured
gateway address instead of the fixed-host path adapter.
