--
-- Copyright 2026 The Jahaziel Authors
-- SPDX-License-Identifier: Apache-2.0
--

-- ============================================================
-- Provider-neutral identity links. The durable key is the
-- (provider, subject) pair from AuthIdentity; email is profile
-- data only and is never used to join accounts.
-- ============================================================
CREATE TABLE user_identity (
    provider      TEXT NOT NULL,
    subject       TEXT NOT NULL,
    user_id       UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    email         TEXT,
    display_name  TEXT,
    linked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, subject)
);

CREATE INDEX idx_user_identity_user ON user_identity (user_id);
