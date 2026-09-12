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
| Confidentiality of credentials and secrets | Environment variables; systemd `EnvironmentFile` at 0600; no secrets in repo |
| Integrity of the base image and shared caches | SHA-256 on all artifacts; signed manifests; verified on every node-agent startup |

## Properties not defended

- A compromised KVM hypervisor (a Linux kernel bug; already lost).
- A compromised Firecracker binary (consumed upstream and trusted).
- Side-channel attacks (Spectre-class, cache timing; mitigated upstream).
- Physical access to the server (total compromise).

## Attack surface inventory

| Surface | Exposure | Mitigation |
|---|---|---|
| `code.example.com:443` | Public | TLS 1.3 only; HSTS; CSP; per-IP rate limit 100 req/min |
| `*.code.example.com:443` | Public | Same; plus per-user rate limit 1000 req/min |
| Gateway WebSocket upgrade | Public | Signed token in query param; token TTL 15 min; single-use |
| Node agent Unix socket | Localhost | 0600 permission; owned by `jahaziel-node-agent`; uid check on accept |
| Node agent TLS port 9443 | Localhost | mTLS; client cert from platform CA; IP allowlist |
| PostgreSQL port 5432 | Localhost | `listen_addresses = 'localhost'`; password auth; role separation |
| Firecracker API socket | Inside jail | `api.sock` owned by per-VM UID; no network access |
| Caddy admin API `:2019` | Localhost | `admin off` in production config |
| systemd journal | Localhost | Journal forwarded to Prometheus/Loki; no user read access |
