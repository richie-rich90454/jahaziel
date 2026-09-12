<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Architecture overview

One Spring Boot control plane, one node agent per host, one gateway at
the edge, Firecracker microVMs per workspace, PostgreSQL for state,
Btrfs for storage, Caddy for TLS.

- Control plane: identity, authorization, workspaces, runtime, nodes,
  storage, snapshots, networking, terminal, IDE sessions, audit,
  monitoring, policy, extensions, administration. One jar, one port
  for the API, one database.
- Node agent: VMs, disks, TAP devices, cgroups, snapshots. No domain
  logic; the workspace id is opaque to it.
- Gateway: Host-header routing, JWT verification, byte relay to the
  workspace. No control-plane round trip per request.
- Audit: guest to node agent over vsock, node agent to collector,
  collector to partitioned PostgreSQL plus object storage.

Module boundaries are enforced by ArchUnit: a module uses another
module's public interface only, never its internals.
