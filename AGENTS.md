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
`docs/guide/configuration.md`, and `docs/reference/env-vars.md`: canonical
`BASIS_AUTH_*`, with legacy `BASIS_*` aliases still supported. Configuration is
read at startup, so a credential change requires a restart.
