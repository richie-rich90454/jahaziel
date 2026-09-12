#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

SERVER="${1:?usage: open-edit-save.sh <server-url>}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "checking server answers at $SERVER"
curl -fsS "$SERVER" -o /dev/null

echo "writing probe file"
echo "Hello Jahaziel" > "$WORK/probe.txt"
test "$(cat "$WORK/probe.txt")" = "Hello Jahaziel"

echo "smoke open-edit-save passed"
