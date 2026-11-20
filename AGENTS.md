<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# AGENTS.md — Repository rules

## Basis-auth downstream contract

`docs/basis_auth_setup_guide.md` is the downstream integration contract and part
of the product. Any change to endpoints, token claims, `typ` values, lifetimes,
revocation or backchannel behavior, scopes or permissions, client/resource
registration, internal API authentication, error codes, or the example
integration **must update that guide in the same change** (mirroring its section
12). Do not leave the guide stale while changing the implementation.

Keep credential key spellings consistent across the guide,
`docs/guide/configuration.md`, and `docs/reference/env-vars.md`. The canonical
set is lean: `BASIS_AUTH_ISSUER`, `BASIS_AUTH_CLIENT_ID`,
`BASIS_AUTH_CLIENT_SECRET`, `BASIS_AUTH_RESOURCE`,
`BASIS_AUTH_RESOURCE_SECRET`, `BASIS_AUTH_INTERNAL_URL`,
`BASIS_AUTH_COOKIE_SECRET` (at least 32 bytes), and `CURRENT_URL_ORIGIN`.
Callbacks are `${CURRENT_URL_ORIGIN}/api/auth/basis/callback` and the
backchannel receiver is `${CURRENT_URL_ORIGIN}/api/auth/basis/backchannel`.
Legacy `BASIS_*` names are aliases only; do not present
`BASIS_AUTH_SCOPES`, `BASIS_AUTH_OFFLINE_ACCESS`,
`BASIS_ADMIN_BOOTSTRAP_EMAIL`, `BASIS_ADMIN_REQUIRED_ACR`, or permissions as
canonical setup. Optional Microsoft Entra keys are `MICROSOFT_TENANT_ID`,
`MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, and
`MICROSOFT_WEBHOOK_STATE` / `MICROSOFT_WEBHOOK_ID`. The app uses SQLite by
default; do not introduce a PostgreSQL `DATABASE_URL`. Configuration is read
at startup, so a credential change requires a restart.

## Style

- New and touched Java stays at AP Computer Science A level: plain and abstract
  classes and constructs teachable in AP CSA. The build still uses JDK 25 and
  targets Java 25 bytecode.
- Use 4-space indentation in Java and TypeScript. TypeScript is formatted with
  `oxfmt`; check it with `pnpm --dir web run format:check`.

## Commits

- One cohesive behavior per commit; prefer single-file commits where the
  change allows. Stage only intended paths — never `git add .` or
  `git commit -a`.
- Conventional messages: `<type>(<scope>): <subject>`, imperative, lowercase,
  no period, max 72 characters; explain why in the body, not what.

## Tooling

- No global installs. Require JDK 25, Node 24, and pnpm 12.14.0 to already
  exist; use the wrapper (`./mvnw`, `mvnw.cmd`) and `dev.ps1` with
  repository-local caches.

## Acceptance

- When a gate, workflow, or test command changes, update
  `docs/operations/acceptance.md` and `docs/operations/release-acceptance.md`
  in the same change.
