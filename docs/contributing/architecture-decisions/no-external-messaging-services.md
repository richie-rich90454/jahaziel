<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# No external messaging services

- Status: accepted
- Date: 2026-09-11
- Supersedes: ADR-0002

Event volume is tiny (about a thousand domain events a day at fifty users),
cache needs are bounded and short-lived, and durability lives in PostgreSQL.
A second stateful service would add operations, backup, security, and
monitoring cost for zero benefit. In-process memory plus PostgreSQL plus the
filesystem carry the platform until measured evidence proves insufficiency.
Auth, metadata, and policy caches are Caffeine with explicit size limits and
TTLs; domain events travel on the in-process bus.
