#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

cd "$(dirname "$0")"
SERVER="${1:-../../out/jahaziel-code-server.tar.zst}"
ALLOW="${ALLOW_HOST:-open-vsx.org}"

if [ ! -f "$SERVER" ]; then
  echo "server artifact missing: $SERVER" >&2
  exit 1
fi

CAPTURE="$(mktemp -d)/capture.pcap"
tcpdump -i any -w "$CAPTURE" 'tcp or udp port 53' >/dev/null 2>&1 &
TCPDUMP_PID=$!
trap 'kill $TCPDUMP_PID 2>/dev/null || true' EXIT

echo "capture running; start the IDE session manually against $ALLOW"
echo "press enter when the scripted session finishes"
read -r _

kill $TCPDUMP_PID 2>/dev/null || true
echo "checking capture against blocklist.txt"

FAIL=0
while read -r pattern; do
  case "$pattern" in
    ""|\#*) continue ;;
  esac
  if tcpdump -nn -r "$CAPTURE" 2>/dev/null | grep -qi "$pattern"; then
    echo "FORBIDDEN connection matched: $pattern"
    FAIL=1
  fi
done < blocklist.txt

if [ "$FAIL" -ne 0 ]; then
  echo "no-network test FAILED"
  exit 1
fi
echo "no-network test passed"
