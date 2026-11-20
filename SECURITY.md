<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Security

Threat model and attack surface inventory live in
[`docs/security/threat-model.md`](docs/security/threat-model.md). Hard
constraints (host isolation, cross-workspace isolation, audit integrity,
crash recovery, noisy-neighbor limits, browser matrix) each have a
corresponding automated check; green CI does not replace the release gates
in [`docs/operations/release-acceptance.md`](docs/operations/release-acceptance.md).

## Reporting vulnerabilities

Report vulnerabilities privately to the maintainers at
security@example.com. Include steps to reproduce, the affected component,
the server architecture, the Java version, and relevant redacted logs. Never
attach `.env`, private keys, workspace source, or unredacted support bundles.
Expect acknowledgment within 48 hours. Do not open a public issue for an
unpatched vulnerability.

## Secret handling

- Keep `.env` private (`chmod 0600`) and out of version control. Values are
  read at startup, so a credential change requires a restart.
- The canonical basis-auth keys are lean: `BASIS_AUTH_ISSUER`,
  `BASIS_AUTH_CLIENT_ID`, `BASIS_AUTH_CLIENT_SECRET`, `BASIS_AUTH_RESOURCE`,
  `BASIS_AUTH_RESOURCE_SECRET`, `BASIS_AUTH_INTERNAL_URL`,
  `BASIS_AUTH_COOKIE_SECRET` (at least 32 bytes), and `CURRENT_URL_ORIGIN`.
  `CURRENT_URL_ORIGIN` sets the callback
  (`${CURRENT_URL_ORIGIN}/api/auth/basis/callback`) and backchannel receiver
  (`${CURRENT_URL_ORIGIN}/api/auth/basis/backchannel`). Legacy `BASIS_*` names
  are aliases only; `BASIS_AUTH_SCOPES`, `BASIS_AUTH_OFFLINE_ACCESS`,
  `BASIS_ADMIN_BOOTSTRAP_EMAIL`, and `BASIS_ADMIN_REQUIRED_ACR` are not
  canonical setup. Optional Microsoft Entra keys are `MICROSOFT_TENANT_ID`,
  `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, and
  `MICROSOFT_WEBHOOK_STATE` / `MICROSOFT_WEBHOOK_ID`. Storage is SQLite by
  default; there is no PostgreSQL `DATABASE_URL`. Legacy
  `BASIS_REDIRECT_URI` / `BASIS_CLIENT_ID` / `BASIS_CLIENT_SECRET` /
  `BASIS_RESOURCE` names and `POST /api/auth/backchannel` remain accepted
  aliases, but the canonical contract above is preferred for new deployments.
- Keep client and resource secrets server-side, never log tokens,
  authorization codes, secrets, verifiers, or raw cookies, and rotate
  credentials per the downstream guide. Set `JAHAZIEL_SHARE_SECRET` to keep
  share-link signing stable across restarts.

## Supported deployment boundary

- One jar, one application port (default `11451`), on Linux, macOS, and
  Windows. Bind to loopback behind a TLS-terminating reverse proxy that
  forwards WebSockets; do not publish the application port directly.
- For mutually trusted users only. Workspace terminals, tasks, and Git
  operations run as native host processes under the service account: there is
  no operating-system isolation between workspaces and no hostile
  multi-tenant security. Linux x64 and arm64 are CI-verified; real-host
  acceptance on macOS and Windows is still open.
- Demo mode binds to loopback only. Never expose it to a network or use it
  with sensitive data.

## Downstream contract

The maintained basis-auth integration contract is
[`docs/basis_auth_setup_guide.md`](docs/basis_auth_setup_guide.md). Endpoints,
token claims and `typ` values, lifetimes, revocation and backchannel behavior,
registration, internal API authentication, error codes, and the example
integration must stay in sync with it.
