--
-- Copyright 2026 The Jahaziel Authors
-- SPDX-License-Identifier: Apache-2.0
--

-- ============================================================
-- Users and identity
-- ============================================================
CREATE TABLE app_user (
    user_id          UUID PRIMARY KEY,
    external_id      TEXT UNIQUE NOT NULL,          -- OIDC subject
    email            TEXT NOT NULL,
    display_name     TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at    TIMESTAMPTZ,
    status           TEXT NOT NULL CHECK (status IN ('active', 'suspended', 'revoked')),
    mfa_enabled      BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_user_email ON app_user (email);
CREATE INDEX idx_user_status ON app_user (status);

CREATE TABLE user_role (
    user_id          UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    role             TEXT NOT NULL,
    granted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by       TEXT NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE user_group (
    user_id          UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    group_name       TEXT NOT NULL,
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, group_name)
);

-- ============================================================
-- Workspaces
-- ============================================================
CREATE TABLE workspace (
    workspace_id     UUID PRIMARY KEY,
    owner_user_id    UUID NOT NULL REFERENCES app_user(user_id),
    name             TEXT NOT NULL,
    state            TEXT NOT NULL,
    state_changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    logical_quota_bytes BIGINT NOT NULL DEFAULT 21474836480,   -- 20 GiB
    inode_quota      BIGINT NOT NULL DEFAULT 1000000,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    UNIQUE (owner_user_id, name)
);
CREATE INDEX idx_workspace_owner ON workspace (owner_user_id);
CREATE INDEX idx_workspace_state ON workspace (state);

CREATE TABLE workspace_acl (
    workspace_id     UUID NOT NULL REFERENCES workspace(workspace_id) ON DELETE CASCADE,
    grantee_user_id  UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    permission       TEXT NOT NULL CHECK (permission IN ('read', 'write', 'admin')),
    granted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by       TEXT NOT NULL,
    PRIMARY KEY (workspace_id, grantee_user_id, permission)
);

-- ============================================================
-- Nodes (declared before virtual machines reference it)
-- ============================================================
CREATE TABLE node (
    node_id          TEXT PRIMARY KEY,
    name             TEXT NOT NULL,
    region           TEXT NOT NULL,
    state            TEXT NOT NULL,
    cpu_cores        INT NOT NULL,
    memory_mib       BIGINT NOT NULL,
    disk_gib         BIGINT NOT NULL,
    registered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_heartbeat   TIMESTAMPTZ,
    cert_fingerprint TEXT NOT NULL
);

-- ============================================================
-- Virtual machines
-- ============================================================
CREATE TABLE virtual_machine (
    vm_id            UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspace(workspace_id) ON DELETE CASCADE,
    node_id          TEXT NOT NULL REFERENCES node(node_id),
    state            TEXT NOT NULL,
    vcpu_count       INT NOT NULL,
    memory_mib       BIGINT NOT NULL,
    disk_path        TEXT NOT NULL,
    home_path        TEXT NOT NULL,
    jail_path        TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    destroyed_at     TIMESTAMPTZ
);
CREATE INDEX idx_vm_workspace ON virtual_machine (workspace_id);
CREATE INDEX idx_vm_node ON virtual_machine (node_id);
CREATE INDEX idx_vm_state ON virtual_machine (state);

-- ============================================================
-- Snapshots
-- ============================================================
CREATE TABLE snapshot (
    snapshot_id          UUID PRIMARY KEY,
    workspace_id         UUID NOT NULL REFERENCES workspace(workspace_id) ON DELETE CASCADE,
    tag                  TEXT NOT NULL,
    kind                 TEXT NOT NULL CHECK (kind IN ('auto', 'manual', 'pre-delete')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           TEXT NOT NULL,
    btrfs_subvolume_path TEXT NOT NULL,
    logical_bytes        BIGINT NOT NULL,
    physical_bytes       BIGINT NOT NULL,
    parent_snapshot_id   UUID REFERENCES snapshot(snapshot_id),
    sha256_of_manifest   TEXT NOT NULL,
    deleted_at           TIMESTAMPTZ,
    UNIQUE (workspace_id, tag)
);
CREATE INDEX idx_snapshot_workspace_created ON snapshot (workspace_id, created_at DESC);
CREATE INDEX idx_snapshot_kind_created ON snapshot (kind, created_at DESC);

-- ============================================================
-- Ports
-- ============================================================
CREATE TABLE workspace_port (
    workspace_id     UUID NOT NULL REFERENCES workspace(workspace_id) ON DELETE CASCADE,
    port             INT NOT NULL CHECK (port >= 1 AND port <= 65535),
    protocol         TEXT NOT NULL CHECK (protocol IN ('http', 'https', 'tcp')),
    registered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, port)
);
CREATE INDEX idx_port_lookup ON workspace_port (workspace_id, port);

-- ============================================================
-- Policies
-- ============================================================
CREATE TABLE policy (
    policy_id        UUID PRIMARY KEY,
    scope            TEXT NOT NULL CHECK (scope IN ('global', 'user', 'workspace')),
    scope_id         TEXT,                                -- NULL for global
    policy_json      JSONB NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       TEXT NOT NULL
);
CREATE INDEX idx_policy_scope ON policy (scope, scope_id);

-- ============================================================
-- Extensions
-- ============================================================
CREATE TABLE extension_policy (
    policy_id        UUID PRIMARY KEY,
    scope            TEXT NOT NULL CHECK (scope IN ('global', 'user', 'workspace')),
    scope_id         TEXT,
    extension_id     TEXT NOT NULL,                       -- publisher.name
    action           TEXT NOT NULL CHECK (action IN ('allow', 'block', 'require_approval')),
    version_constraint TEXT,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_extension_policy_scope ON extension_policy (scope, scope_id);

-- ============================================================
-- Cache artifacts
-- ============================================================
CREATE TABLE cache_artifact (
    artifact_id      UUID PRIMARY KEY,
    tool             TEXT NOT NULL CHECK (tool IN ('maven', 'gradle', 'cargo', 'go', 'npm', 'pnpm', 'pip')),
    coordinate       TEXT NOT NULL,                       -- e.g. "com.google.guava:guava:32.0.0"
    sha256           TEXT NOT NULL,
    size_bytes       BIGINT NOT NULL,
    path_on_host     TEXT NOT NULL,
    populated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    source           TEXT NOT NULL CHECK (source IN ('curated', 'warm', 'read-through', 'admin')),
    UNIQUE (tool, coordinate, sha256)
);
CREATE INDEX idx_cache_tool_coord ON cache_artifact (tool, coordinate);

-- ============================================================
-- Audit (partitioned by month)
-- ============================================================
CREATE TABLE audit_event (
    event_id         UUID NOT NULL,
    timestamp        TIMESTAMPTZ NOT NULL,
    actor            TEXT NOT NULL,
    actor_type       TEXT NOT NULL,
    action           TEXT NOT NULL,
    target           TEXT,
    target_type      TEXT,
    outcome          TEXT NOT NULL,
    source_ip        TEXT,
    source_node      TEXT,
    details          JSONB NOT NULL DEFAULT '{}'::jsonb,
    prev_hash        TEXT,
    hash             TEXT NOT NULL,
    PRIMARY KEY (event_id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Example partitions
CREATE TABLE audit_event_2026_09 PARTITION OF audit_event
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_event_2026_10 PARTITION OF audit_event
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

CREATE INDEX idx_audit_actor ON audit_event (actor, timestamp DESC);
CREATE INDEX idx_audit_action ON audit_event (action, timestamp DESC);
CREATE INDEX idx_audit_target ON audit_event (target, timestamp DESC);

-- ============================================================
-- Audit: terminal command detail (partitioned by month)
-- ============================================================
CREATE TABLE audit_terminal (
    record_id        UUID NOT NULL,
    event_id         UUID NOT NULL,
    timestamp        TIMESTAMPTZ NOT NULL,
    user_id          UUID NOT NULL,
    workspace_id     UUID NOT NULL,
    terminal_session_id UUID NOT NULL,
    command          TEXT NOT NULL,
    exit_code        INT NOT NULL,
    cwd              TEXT NOT NULL,
    env_hash         TEXT,
    duration_ms      BIGINT,
    stdout_object_id TEXT,
    stderr_object_id TEXT,
    stdout_size      BIGINT,
    stderr_size      BIGINT,
    PRIMARY KEY (record_id, timestamp)
) PARTITION BY RANGE (timestamp);

CREATE INDEX idx_audit_terminal_user ON audit_terminal (user_id, timestamp DESC);
CREATE INDEX idx_audit_terminal_ws ON audit_terminal (workspace_id, timestamp DESC);

CREATE TABLE audit_terminal_2026_09 PARTITION OF audit_terminal
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_terminal_2026_10 PARTITION OF audit_terminal
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

-- ============================================================
-- Audit: process execution (Layer 2)
-- ============================================================
CREATE TABLE audit_process (
    record_id        UUID NOT NULL,
    timestamp        TIMESTAMPTZ NOT NULL,
    workspace_id     UUID NOT NULL,
    pid              INT NOT NULL,
    ppid             INT,
    exe_path         TEXT NOT NULL,
    argv             TEXT[] NOT NULL,
    exit_code        INT,
    start_time       TIMESTAMPTZ NOT NULL,
    end_time         TIMESTAMPTZ,
    uid              INT NOT NULL,
    gid              INT NOT NULL,
    capabilities     TEXT[],
    PRIMARY KEY (record_id, timestamp)
) PARTITION BY RANGE (timestamp);

CREATE INDEX idx_audit_process_ws ON audit_process (workspace_id, timestamp DESC);
CREATE INDEX idx_audit_process_exe ON audit_process (exe_path, timestamp DESC);

CREATE TABLE audit_process_2026_09 PARTITION OF audit_process
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_process_2026_10 PARTITION OF audit_process
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

-- ============================================================
-- Resource metrics (rolled up hourly)
-- ============================================================
CREATE TABLE resource_metric_hourly (
    workspace_id     UUID NOT NULL REFERENCES workspace(workspace_id) ON DELETE CASCADE,
    hour_start       TIMESTAMPTZ NOT NULL,
    cpu_avg_percent  REAL NOT NULL,
    cpu_max_percent  REAL NOT NULL,
    memory_avg_mib   BIGINT NOT NULL,
    memory_max_mib   BIGINT NOT NULL,
    disk_read_avg_bps BIGINT NOT NULL,
    disk_write_avg_bps BIGINT NOT NULL,
    network_rx_avg_bps BIGINT NOT NULL,
    network_tx_avg_bps BIGINT NOT NULL,
    process_max      INT NOT NULL,
    PRIMARY KEY (workspace_id, hour_start)
);

CREATE INDEX idx_resource_metric_workspace_hour ON resource_metric_hourly (workspace_id, hour_start DESC);

-- ============================================================
-- Administrative actions
-- ============================================================
CREATE TABLE admin_action (
    action_id        UUID PRIMARY KEY,
    timestamp        TIMESTAMPTZ NOT NULL DEFAULT now(),
    admin_user_id    UUID NOT NULL REFERENCES app_user(user_id),
    action           TEXT NOT NULL,
    target           TEXT,
    details          JSONB NOT NULL DEFAULT '{}'::jsonb,
    reason           TEXT NOT NULL,
    break_glass      BOOLEAN NOT NULL DEFAULT false,
    approver_user_id UUID REFERENCES app_user(user_id)
);
CREATE INDEX idx_admin_action_time ON admin_action (timestamp DESC);
CREATE INDEX idx_admin_action_admin ON admin_action (admin_user_id, timestamp DESC);

-- ============================================================
-- Comments (for AI-agent legibility)
-- ============================================================
COMMENT ON TABLE app_user IS 'User accounts. Populated from the external IdP on first login.';
COMMENT ON COLUMN app_user.external_id IS 'The "sub" claim from the OIDC provider. Unique per user.';
COMMENT ON COLUMN app_user.mfa_enabled IS 'True if the user has completed an MFA enrollment. Required for admin accounts.';
COMMENT ON TABLE workspace IS 'A user''s isolated Linux development environment.';
COMMENT ON COLUMN workspace.state IS 'One of: requested, preparing, booting, ready, active, idle, suspended, destroyed, failed.';
COMMENT ON TABLE snapshot IS 'Point-in-time snapshots of a workspace''s home subvolume.';
COMMENT ON COLUMN snapshot.kind IS 'auto = platform-managed daily; manual = user-triggered; pre-delete = created before admin deletion.';
COMMENT ON TABLE audit_event IS 'Append-only audit log. Partitioned by month; old partitions are dropped, never DELETEd.';
COMMENT ON TABLE audit_terminal IS 'Layer 1 audit: command, stdout, stderr, exit code per terminal command.';
COMMENT ON TABLE audit_process IS 'Layer 2 audit: process execution traced via eBPF in the guest.';
