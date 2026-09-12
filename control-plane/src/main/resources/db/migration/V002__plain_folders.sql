--
-- Copyright 2026 The Jahaziel Authors
-- SPDX-License-Identifier: Apache-2.0
--

-- ============================================================
-- Plain folders: workspaces are ordinary directories, not disk
-- images or subvolumes. Drops the per-VM disk path and renames
-- the snapshot path column to match.
-- ============================================================
ALTER TABLE virtual_machine DROP COLUMN disk_path;

ALTER TABLE snapshot RENAME COLUMN btrfs_subvolume_path TO snapshot_path;

COMMENT ON TABLE snapshot IS 'Point-in-time snapshots of a workspace''s home folder.';
