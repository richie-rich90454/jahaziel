#!/usr/bin/env bash
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#
# Quarterly restore drill against a sandbox node. Covers bare-metal host
# restore, single-workspace restore, single-snapshot restore, and
# point-in-time PostgreSQL restore. Record the result, do not automate
# the verdict: a human signs off.
set -euo pipefail

SANDBOX="${1:?usage: restore-drill.sh <sandbox-host>}"
DATE="$(date +%Y-%m-%d)"

echo "[$DATE] restore drill against $SANDBOX"
echo "1. bare-metal host restore:provision $SANDBOX from backup, boot, check health"
echo "2. single-workspace restore: pick a canary workspace, restore home, diff"
echo "3. single-snapshot restore: restore one snapshot tag, boot, diff"
echo "4. point-in-time postgres restore: recover to latest WAL, count audit rows"

echo "drill complete; append the signed result to docs/operations/restore-drill-log.md"
