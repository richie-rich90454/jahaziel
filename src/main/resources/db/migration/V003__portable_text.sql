--
-- Copyright 2026 The Jahaziel Authors
-- SPDX-License-Identifier: Apache-2.0
--

-- ============================================================
-- Portable text storage: JSON and string arrays live in TEXT on
-- every database. No query uses JSON or array operators, so the
-- content is byte-identical; only the column type changes.
-- ============================================================
ALTER TABLE admin_action ALTER COLUMN details TYPE TEXT USING details::text;

ALTER TABLE audit_event ALTER COLUMN details TYPE TEXT USING details::text;

ALTER TABLE policy ALTER COLUMN policy_json TYPE TEXT USING policy_json::text;

ALTER TABLE audit_process ALTER COLUMN argv TYPE TEXT USING array_to_json(argv)::text;

ALTER TABLE audit_process ALTER COLUMN capabilities TYPE TEXT USING array_to_json(capabilities)::text;
