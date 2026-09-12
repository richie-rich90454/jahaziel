#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

cd "$(dirname "$0")/out"

sha256sum ./*.tar.zst > SHA256SUMS

if [ -z "${JAHAZIEL_SIGNING_KEY:-}" ]; then
  echo "JAHAZIEL_SIGNING_KEY is not set" >&2
  exit 1
fi

minisign -S -s "$JAHAZIEL_SIGNING_KEY" -m SHA256SUMS
echo "Signed SHA256SUMS"
