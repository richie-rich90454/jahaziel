<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Ports

Last verified against version 0.1.0-SNAPSHOT.

| Port | Default | Meaning |
|---|---|---|
| API | `11451` (`PORT`, `--port`) | UI, API, `/_admin`, `/actuator/health` |
| Gateway | `8081` (`--gateway-port`) | Workspace routing and session verification |

Both bind loopback. The REH server listens on a random localhost port with
no token; the gateway proxies HTTP and WebSocket to it. Change the API port
with `PORT` in `.env` and re-run `doctor` to confirm it is free.
