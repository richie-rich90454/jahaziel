<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# dev-idp (stub)

Local stub IdP for `--demo` and the `dev` profile. Test logins only,
never production: it mints unsigned demo tokens for one fixed user and
answers no real OIDC discovery.

## Contract

- Listens on `http://localhost:4444` (`BASIS_AUTH_ISSUER` in
  `src/main/resources/application-dev.yml` points here).
- `GET /.well-known/openid-configuration` returns the stub document.
- `GET /authorize` redirects straight back with `?code=demo&state=...`.
- `POST /token` swaps `code=demo` for a demo id token.
- `GET /userinfo` returns the demo user (`demo@jahaziel.local`).
- `GET /jwks` returns an empty key set; the server accepts demo tokens
  only when `JAHAZIEL_DEMO=true` (or `--demo`).

## Demo seed

With `--demo` the server skips all `BASIS_*` checks, trusts this stub,
and seeds on first boot:

- user `demo@jahaziel.local` (admin, MFA disabled),
- workspace `demo` on the `local` backend.

## Status

Stub only: no server code here yet. Until it lands, `--demo` uses the
in-process stub wired by the auth owner (same contract as above).
