<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Docs contract

Every phase's "Done when" includes its docs. Code changes without matching
doc updates are incomplete. When in doubt, prefer deleting a stale doc over
updating it. Keep [naming](../naming.md), the architecture docs, and the user
guides describing the same product.

## Mandatory same-change updates

The repository `AGENTS.md` defines these product contracts;
they are part of "done", not optional follow-ups:

- `docs/basis_auth_setup_guide.md` is the downstream integration contract.
  Any change to endpoints, token claims, `typ` values, lifetimes, revocation or
  backchannel behavior, scopes or permissions, client/resource registration,
  internal API authentication, error codes, or the example integration must
  update that guide **in the same change** (its section 12). Keep credential
  spellings consistent across the guide,
  `docs/guide/configuration.md`, and `docs/reference/env-vars.md`: canonical
  `BASIS_AUTH_*`, legacy `BASIS_*` aliases only. Configuration is read at
  startup, so a credential change requires a restart.
- When a gate, workflow, or test command changes, update
  `docs/operations/acceptance.md` and
  `docs/operations/release-acceptance.md` in the same change.
- When architecture or security behavior changes, update the affected page in
  `docs/architecture/` and `docs/security/threat-model.md` in the same change.
- Supersede an ADR in place with an explicit `Status: superseded by ...` line
  and a link to the replacement; do not silently rewrite history.

## Style and commits

Follow the short [style guide](style-guide.md) for indentation, Java/TypeScript
style, and one-change-per-commit practice. Deleted or clearly retired docs beat
stale ones: mark historical IDE material as retired and point readers to the
maintained workbench docs.
