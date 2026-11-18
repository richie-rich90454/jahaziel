--
-- Copyright 2026 The Jahaziel Authors
-- SPDX-License-Identifier: Apache-2.0
--

-- ============================================================
-- Collaboration: membership, comments, live sessions, share links.
-- Ids are TEXT and timestamps are epoch milliseconds so the same
-- schema works on SQLite and PostgreSQL. No global state is used.
-- ============================================================
CREATE TABLE workspace_member (
    workspace_id     TEXT NOT NULL,
    user_id          TEXT NOT NULL,
    role             TEXT NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    joined_at        BIGINT NOT NULL,
    PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX idx_workspace_member_user ON workspace_member (user_id);

CREATE TABLE workspace_invitation (
    invitation_id    TEXT PRIMARY KEY,
    workspace_id     TEXT NOT NULL,
    invitee          TEXT NOT NULL,
    role             TEXT NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    invited_by       TEXT NOT NULL,
    created_at       BIGINT NOT NULL,
    expires_at       BIGINT NOT NULL,
    accepted         INTEGER NOT NULL DEFAULT 0,
    accepted_at      BIGINT
);

CREATE INDEX idx_workspace_invitation_workspace ON workspace_invitation (workspace_id);

CREATE TABLE file_comment (
    comment_id       TEXT PRIMARY KEY,
    workspace_id     TEXT NOT NULL,
    path             TEXT NOT NULL,
    start_line       INTEGER NOT NULL,
    end_line         INTEGER NOT NULL,
    author           TEXT NOT NULL,
    body             TEXT NOT NULL,
    resolved         INTEGER NOT NULL DEFAULT 0,
    created_at       BIGINT NOT NULL,
    updated_at       BIGINT NOT NULL
);

CREATE INDEX idx_file_comment_workspace ON file_comment (workspace_id, path);

CREATE TABLE file_comment_reply (
    reply_id         TEXT PRIMARY KEY,
    comment_id       TEXT NOT NULL,
    author           TEXT NOT NULL,
    body             TEXT NOT NULL,
    created_at       BIGINT NOT NULL
);

CREATE INDEX idx_file_comment_reply_comment ON file_comment_reply (comment_id);

CREATE TABLE live_session (
    session_id       TEXT PRIMARY KEY,
    workspace_id     TEXT NOT NULL,
    controller       TEXT NOT NULL,
    token_hash       TEXT NOT NULL UNIQUE,
    active           INTEGER NOT NULL DEFAULT 1,
    created_at       BIGINT NOT NULL
);

CREATE INDEX idx_live_session_workspace ON live_session (workspace_id);

CREATE TABLE live_session_observer (
    session_id       TEXT NOT NULL,
    user_id          TEXT NOT NULL,
    consent_at       BIGINT NOT NULL,
    PRIMARY KEY (session_id, user_id)
);

CREATE TABLE share_link (
    share_id         TEXT PRIMARY KEY,
    workspace_id     TEXT NOT NULL,
    token_hash       TEXT NOT NULL UNIQUE,
    scope            TEXT NOT NULL CHECK (scope IN ('VIEW', 'EDIT')),
    one_time         INTEGER NOT NULL DEFAULT 0,
    used             INTEGER NOT NULL DEFAULT 0,
    revoked          INTEGER NOT NULL DEFAULT 0,
    expires_at       BIGINT NOT NULL,
    created_by       TEXT NOT NULL,
    created_at       BIGINT NOT NULL
);

CREATE INDEX idx_share_link_workspace ON share_link (workspace_id);

CREATE TABLE collaboration_audit (
    audit_id         TEXT PRIMARY KEY,
    workspace_id     TEXT NOT NULL,
    actor            TEXT NOT NULL,
    action           TEXT NOT NULL,
    target           TEXT,
    details          TEXT NOT NULL,
    created_at       BIGINT NOT NULL
);

CREATE INDEX idx_collaboration_audit_workspace ON collaboration_audit (workspace_id, created_at);
