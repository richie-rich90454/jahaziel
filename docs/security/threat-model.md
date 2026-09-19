<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Threat model

## Adversaries

| ID | Adversary | Capabilities | Primary defense |
|---|---|---|---|
| A1 | Curious student | Unprivileged account in the workspace VM | File permissions, KVM isolation, no sudo, no setuid binaries in base image |
| A2 | Malicious insider | Unprivileged account, tries to attack others | Network namespace isolation, no shared writable caches, no shared process trees |
| A3 | Compromised workspace | Arbitrary code execution inside the VM | Firecracker + Jailer + seccomp + minimal device model + Landlock |
| A4 | Internet scanner | Reaches wildcard domain | Gateway auth, no direct VM exposure, per-IP rate limits, no directory listing |
| A5 | Careless user | Accidentally destructive | Quotas, snapshots, no automatic deletion, soft warnings at 80% |
| A6 | Compromised admin account | Full admin credentials | Full audit trail, no silent tampering, break-glass requires two admins |
| A7 | Compromised control plane | JVM compromise | Separate credentials per service, limited blast radius, audit runs on a separate service account |

## Properties defended

| Property | How |
|---|---|
| Confidentiality of each user's workspace contents | KVM isolation; per-VM UID; no shared mount of `/home` |
| Integrity of each user's workspace contents | COW snapshots; no writable shared mount; audit log of modifications |
| Availability of the platform to all users | cgroup limits; admission control; per-user quotas |
| Integrity of the audit record | Append-only; Merkle chain; separate service account; no `DELETE` role |
| Confidentiality of credentials and secrets | Environment variables; the supervisor reads `EnvironmentFile` at 0600; no secrets in repo |
| Integrity of the base image and shared caches | SHA-256 on all artifacts; signed manifests; verified on every node-agent startup |

## Properties not defended

- A compromised KVM hypervisor (a Linux kernel bug; already lost).
- A compromised Firecracker binary (consumed upstream and trusted).
- Side-channel attacks (Spectre-class, cache timing; mitigated upstream).
- Physical access to the server (total compromise).

## Attack surface inventory

| Surface | Exposure | Mitigation |
|---|---|---|
| `code.example.com:443` | Public | TLS 1.3 only; HSTS; CSP; aggregate L3 limits in nftables |
| `*.code.example.com:443` | Public | Same; gateway rejects missing/invalid tokens with 401 |
| Gateway WebSocket upgrade | Public | Signed token in query param; 15 min default TTL |
| Sync relay vsock :9002 | Guest-initiated only | socat byte relay; per-workspace secret in hello frame; 30 s timeout |
| PostgreSQL port 5432 | Localhost | `listen_addresses = 'localhost'`; password auth; role separation |
| Firecracker API socket | Inside jail | `api.sock` owned by per-VM UID; no network access |
| Caddy admin API `:2019` | Localhost | `admin off` in production config |
| supervisor per-service log files under `data/logs` | Localhost | No user read access |

## Not yet implemented

These mitigations are planned and must land before hostile multi-tenant use:
per-IP/per-user L7 rate limits, single-use WebSocket tokens, node-agent
mTLS on TLS port 9443, node-agent Unix socket with an accept uid check,
and journal forwarding to Prometheus/Loki.
