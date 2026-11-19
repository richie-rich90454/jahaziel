--
-- Copyright 2026 The Jahaziel Authors
-- SPDX-License-Identifier: Apache-2.0
--

-- ============================================================
-- Workspace safety: operation history, trash index, and flag overrides.
-- The runtime keeps authoritative state in the jar data directory;
-- these rows document the same contract for SQL-backed deployments.
-- IDs are TEXT and timestamps are epoch milliseconds so the schema
-- works on SQLite and PostgreSQL alike. Git metadata is never stored.
-- ============================================================
CREATE TABLE workspace_operation (
    operation_id     TEXT PRIMARY KEY,
    workspace_id     TEXT NOT NULL,
    sequence         BIGINT NOT NULL,
    kind             TEXT NOT NULL CHECK (kind IN ('WRITE', 'CREATE', 'DELETE', 'RENAME', 'COPY', 'MKDIR')),
    path             TEXT NOT NULL,
    old_path         TEXT,
    old_bytes        BLOB,
    new_bytes        BLOB,
    applied          INTEGER NOT NULL DEFAULT 1,
    actor            TEXT NOT NULL,
    created_at       BIGINT NOT NULL
);

CREATE INDEX idx_workspace_operation_history
    ON workspace_operation (workspace_id, sequence);

CREATE TABLE workspace_trash_item (
    item_id          TEXT PRIMARY KEY,
    workspace_id     TEXT NOT NULL,
    path             TEXT NOT NULL,
    kind             TEXT NOT NULL CHECK (kind IN ('FILE', 'DIRECTORY')),
    size_bytes       BIGINT NOT NULL,
    deleted_at       BIGINT NOT NULL,
    expires_at       BIGINT NOT NULL
);

CREATE INDEX idx_workspace_trash_item_workspace
    ON workspace_trash_item (workspace_id, deleted_at);

CREATE TABLE feature_flag_override (
    flag_name        TEXT NOT NULL,
    scope            TEXT NOT NULL CHECK (scope IN ('global', 'user', 'workspace')),
    target           TEXT NOT NULL,
    enabled          INTEGER NOT NULL DEFAULT 0,
    updated_at       BIGINT NOT NULL,
    PRIMARY KEY (flag_name, scope, target)
);
