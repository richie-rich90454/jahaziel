<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Changelog

Follows Keep a Changelog. Versions follow Semantic Versioning for the single
product jar; the retired node agent, gateway, base image, and IDE fork no longer
version independently.

## [Unreleased] — 0.2.0

Target release 0.2.0; not yet cut. The project version in the build remains
`0.1.0-SNAPSHOT` until release.

### Added

- Plugin architecture: `dev.jahaziel.plugin` discovers and lifecycle-manages
  plugins, `dev.jahaziel.identity.auth` defines `AuthProviderPlugin` and the
  ordered `AuthPluginRegistry`, and `dev.jahaziel.platform.spi` plus the
  networking internals host per-OS process and port-discovery seams.
- Nine sign-in providers behind that registry: basis-auth, Microsoft Entra ID,
  Google, GitHub, generic OIDC, local password + TOTP, passkey/WebAuthn,
  API token, and LDAP. A provider is enabled only when configured; identity
  links by `(provider, subject)`, never by email.
- Full basis-auth downstream contract: OIDC authorization code + S256 PKCE,
  token `typ`/claim/lifetime rules, rotating refresh with family revocation,
  backchannel logout and access-token revocation at
  `${CURRENT_URL_ORIGIN}/api/auth/basis/backchannel`, resource JWT validation,
  and client/resource secret rotation. Configuration uses the lean canonical
  key set (`BASIS_AUTH_ISSUER`, `BASIS_AUTH_CLIENT_ID`,
  `BASIS_AUTH_CLIENT_SECRET`, `BASIS_AUTH_RESOURCE`,
  `BASIS_AUTH_RESOURCE_SECRET`, `BASIS_AUTH_INTERNAL_URL`,
  `BASIS_AUTH_COOKIE_SECRET` of at least 32 bytes, and `CURRENT_URL_ORIGIN`);
  legacy `BASIS_*` names are aliases only. Storage is SQLite by default.
- Backend modules: collaboration (members, invitations, comments, share links,
  live sessions, audit), Club teaching sessions with roles and checkpoints,
  agent assistant and managed tool seam, data tools (CSV/SQLite, notebook
  cells, diffs, search index), monitoring, and compliance administration.
  These ship domain/service layers without dedicated workbench panels.
- Automated acceptance gates: five-target CI verification, a scripted
  deployment walkthrough per host, and recorded gate status in
  `docs/operations/release-acceptance.md`.

### Changed

- Cross-platform parity: the same jar runs on Linux, macOS, and Windows with
  per-OS service templates; Linux x64 and arm64 remain the CI-verified targets
  while real-host macOS and Windows acceptance stays open.
- Workbench redesigned as a SolidJS + DaisyUI client in `web/src`, built with
  Vite and packaged into the jar. CodeMirror 6, xterm.js, tasks, previews, Git,
  debug, and pinned language packs replace the retired VS Code fork.
- Java sources refactored to AP Computer Science A-level constructs with
  4-space indentation; TypeScript is formatted with `oxfmt`.
- Release acceptance now pairs the human checklist with automated gate records;
  gate changes must update both records in the same change.

### Fixed

- Serialization and transport-name binding in gateway, debug, and networking
  flows, and race-safe cleanup in the deployment acceptance workflow.
