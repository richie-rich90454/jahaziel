<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Ports

| Port | Default | Meaning |
|---|---|---|
| API / workbench | `11451` (`PORT`, `--port`) | Same-origin UI, API, admin page, health endpoint, terminal WebSockets, and preview gateway |

Jahaziel serves one service port. A no-argument boot with a jar-adjacent
`.env` starts the full stack — API, node agent and gateway — on `11451`. The
workbench editor is served from that port; there is no per-workspace IDE
server to start or proxy to. The legacy standalone gateway listener is off by
default (`--gateway-port`, default `8081`, or `JAHAZIEL_GATEWAY_PORT`); local
demo and standalone-gateway deployments may use its address instead of the
fixed-host path adapter below.

## Preview hostname and path scheme

In the packaged deployment, preview URLs use the fixed host
`preview.<JAHAZIEL_DOMAIN>` and the route `/{workspaceId}/{port}/`. Register
one DNS record for the IDE hostname and one for the fixed preview hostname;
wildcard DNS is not needed. Both hosts terminate TLS at the same reverse proxy
and route to port `11451`.

Preview requests resolve to an active workspace port and require a
workspace- and port-scoped token, verified before routing. The preview hostname
isolates project content from the IDE origin, while all preview pages share
that preview origin. Projects that hard-code root-relative asset paths may
need configuration before path-based previews work.

## Candidate discovery

`GET /api/v1/workspaces/{id}/ports/candidates` suggests listening ports owned
by the workspace's live task and terminal process trees. Discovery is read-only
and cross-platform:

| OS | Source |
|---|---|
| Linux | `/proc/net/tcp`, `/proc/net/tcp6` and each tracked PID's `/proc/<pid>/fd` socket links |
| macOS | fixed `lsof -nP -iTCP -sTCP:LISTEN -Fpfn` invocation |
| Windows | fixed `netstat -ano -p TCP`, `LISTENING` rows only |

Only ports held by tracked task/terminal PIDs are reported, capped at 64,
deduplicated, and sorted by port. Detection and parsing failures fail closed
with an empty list; discovery never runs a command from user input.

## Explicit register / open

Candidates are suggestions only — discovery never registers a port or opens a
gateway route. Use `POST /api/v1/workspaces/{id}/ports` to register a port;
the response carries a ready-to-use, tokenized preview URL. Use
`DELETE /api/v1/workspaces/{id}/ports/{port}` to unregister it. Every preview
stays workspace-authorized, and the URL token lifetime is controlled by
`jahaziel.jwt.ttl-seconds` (default `900` seconds).
