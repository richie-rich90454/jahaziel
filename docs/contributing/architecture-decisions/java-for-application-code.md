<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Java for application code

- Status: accepted
- Date: 2026-09-11
- Supersedes: ADR-0001

The platform is a control-plane and orchestration problem, not a
memory-safety problem. Java gives mature orchestration libraries (Spring,
Hibernate, Jackson, Nimbus, Protobuf, gRPC), virtual threads for blocking
I/O at scale, excellent long-lived-service tooling, fast builds, and a far
larger contributor pool. We consume Firecracker and KVM instead of
writing them, so Rust's strengths do not apply to our code. A profiled hot
path may still earn C++, never as a first resort.
