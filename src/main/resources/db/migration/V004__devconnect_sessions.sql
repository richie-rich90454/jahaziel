--
-- Copyright 2026 The Jahaziel Authors
-- SPDX-License-Identifier: Apache-2.0
--

-- ============================================================
-- DevConnect (basis-auth) sessions. The browser holds only an
-- opaque session id; PKCE verifier, state, and nonce live here
-- so logins survive restarts and a single control plane.
-- ============================================================
CREATE TABLE bff_session (
    session_id      TEXT PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bff_session_expires ON bff_session (expires_at);

CREATE INDEX idx_bff_session_user ON bff_session (user_id);

CREATE TABLE oidc_pending (
    state           TEXT PRIMARY KEY,
    nonce           TEXT NOT NULL,
    code_verifier   TEXT NOT NULL,
    redirect_uri    TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_oidc_pending_expires ON oidc_pending (expires_at);
