<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Ports

Last verified against version 0.1.0-SNAPSHOT.

| Port | Default | Meaning |
|---|---|---|
| API | `11451` (`PORT`, `--port`) | UI, API, `/_admin`, `/actuator/health`, and workspace traffic: hosts of the form `<user-slug>-<port>.<domain>` are authenticated by session token and relayed (HTTP and WebSocket) to the workspace backend on loopback |
| Gateway (legacy opt-in) | off (`--gateway-port`, default `8081`) | Standalone workspace relay socket; only starts when `jahaziel.dist.gateway-enabled=true`. The application port above covers the same traffic, so leave this off |

Only the API port is needed. The REH server listens on a random localhost port with
no token; the relay proxies HTTP and WebSocket to it. Change the API port
with `PORT` in `.env` and re-run `doctor` to confirm it is free. Reach a
workspace IDE at `http://<user-slug>-<port>.<domain>:11451/?token=<token>`
(`*.localhost` resolves to loopback in browsers, so `JAHAZIEL_DOMAIN=localhost`
works with no extra setup).
