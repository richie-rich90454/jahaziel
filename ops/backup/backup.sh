#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

STAMP="$(date +%Y-%m-%d)"
LOCAL="${BACKUP_DIR:-/var/backups/jahaziel}/$STAMP"
OFFSITE="${OFFSITE_HOST:-backup.example.com}:/srv/jahaziel/"
mkdir -p "$LOCAL"

echo "workspace homes and snapshots"
rsync -a /var/lib/jahaziel/workspaces/ "$LOCAL/workspaces/"

echo "database base backup"
pg_basebackup -D "$LOCAL/db" -Ft -z -P

echo "audit objects"
rsync -a /var/lib/jahaziel/audit/ "$LOCAL/audit/"

echo "offsite copy"
rsync -a --delete "$LOCAL/" "$OFFSITE$STAMP/"

echo "retention: keep 30 local days"
find "${BACKUP_DIR:-/var/backups/jahaziel}" -mindepth 1 -maxdepth 1 -mtime +30 -exec rm -rf -- {} +

echo "backup $STAMP complete"
