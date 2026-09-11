<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel

Self-hosted browser-based development platform: every authorized developer
gets their own Linux development machine, accessible entirely through a
browser. One login, one browser tab: a real Linux machine, a real IDE, a
real terminal, real isolation, real audit.

Users share infrastructure, never ownership. Isolation is by KVM microVM
(Firecracker), one workspace VM per user. The control plane is a single
Spring Boot jar; the per-host node agent manages VMs; the gateway routes
wildcard hosts to workspaces.

## Layout

- `control-plane/` — orchestration service (Java 25, Spring Boot)
- `node-agent/` — per-host VM manager (Java 25)
- `gateway/` — HTTP/WebSocket entry point (Java 25)
- `spi/` — public plugin interfaces
- `ide/` — fork of VS Code (Jahaziel Code)
- `web/` — browser UI wrapper
- `base-image-builder/` — guest OS image pipeline
- `docs/` — architecture, deployment, operations, security guides
- `ops/` — systemd units, firewall rules, proxy config, backup scripts
- `benchmarks/` — benchmarks backing every shipped optimization
- `examples/`, `third_party/`

## Build

```bash
./mvnw verify
```

Requires JDK 25 and Maven 3.9.x (via the wrapper). See `CONTRIBUTING.md`.

## License

Apache License 2.0. See `LICENSE`.
