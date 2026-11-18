--
-- Copyright 2026 The Jahaziel Authors
-- SPDX-License-Identifier: Apache-2.0
--

-- ============================================================
-- Developer Club (additive; git remains the source of truth)
-- ============================================================
CREATE TABLE club_session (
    session_id          TEXT PRIMARY KEY,
    host_user_id        UUID NOT NULL REFERENCES app_user(user_id),
    template_name       TEXT NOT NULL,
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    join_code           TEXT NOT NULL UNIQUE,
    capacity            INT NOT NULL CHECK (capacity BETWEEN 1 AND 500),
    locked              BOOLEAN NOT NULL DEFAULT false,
    leaderboard_enabled BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_club_session_host ON club_session (host_user_id);
CREATE INDEX idx_club_session_window ON club_session (starts_at, ends_at);

CREATE TABLE club_participant (
    session_id    TEXT NOT NULL REFERENCES club_session(session_id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    role          TEXT NOT NULL CHECK (role IN ('INSTRUCTOR', 'ASSISTANT', 'ATTENDEE', 'OBSERVER')),
    workspace_id  UUID REFERENCES workspace(workspace_id) ON DELETE SET NULL,
    muted         BOOLEAN NOT NULL DEFAULT false,
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at    TIMESTAMPTZ,
    PRIMARY KEY (session_id, user_id)
);
CREATE INDEX idx_club_participant_role ON club_participant (session_id, role);

CREATE TABLE club_step_progress (
    session_id   TEXT NOT NULL REFERENCES club_session(session_id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    lesson_id    TEXT NOT NULL,
    step_id      TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, user_id, lesson_id, step_id)
);
CREATE INDEX idx_club_progress_aggregate ON club_step_progress (session_id, lesson_id, step_id);

CREATE TABLE club_checkpoint (
    submission_id TEXT PRIMARY KEY,
    session_id    TEXT NOT NULL REFERENCES club_session(session_id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    workspace_id  UUID NOT NULL REFERENCES workspace(workspace_id) ON DELETE CASCADE,
    snapshot_id   UUID NOT NULL REFERENCES snapshot(snapshot_id) ON DELETE CASCADE,
    note          TEXT NOT NULL DEFAULT '',
    feedback      TEXT,
    promoted_to   TEXT,
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_club_checkpoint_session ON club_checkpoint (session_id, submitted_at DESC);

CREATE TABLE club_showcase (
    project_id   TEXT PRIMARY KEY,
    title        TEXT NOT NULL,
    description  TEXT NOT NULL DEFAULT '',
    tags         TEXT[] NOT NULL DEFAULT '{}',
    screenshots  TEXT[] NOT NULL DEFAULT '{}',
    source_label TEXT NOT NULL DEFAULT '',
    curator_id   UUID NOT NULL REFERENCES app_user(user_id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE club_session IS 'Optional Developer Club sessions; additive to the platform.';
COMMENT ON COLUMN club_session.join_code IS 'QR join code, rotatable by the instructor.';
COMMENT ON COLUMN club_session.leaderboard_enabled IS 'Off by default; only aggregate progress is exposed otherwise.';
COMMENT ON TABLE club_participant IS 'Club roster with per-session roles. OBSERVER is read-only.';
COMMENT ON TABLE club_step_progress IS 'Per-user step completions; expose only aggregates unless a leaderboard is explicitly enabled.';
COMMENT ON TABLE club_checkpoint IS 'Checkpoint snapshots submitted for instructor review.';
COMMENT ON TABLE club_showcase IS 'Admin-curated read-only previews. No source bytes are stored; git remains the source of truth.';
