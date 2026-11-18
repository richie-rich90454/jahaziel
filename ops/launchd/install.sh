#!/bin/sh
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
# Registers the jar-adjacent Jahaziel deployment with launchd.
# Usage: install.sh /path/to/directory-containing-jahaziel.jar [label]

set -eu

if [ "$#" -lt 1 ]; then
    echo "usage: $0 <jar-directory> [label]" >&2
    exit 2
fi

JAR_DIR=$(cd "$1" && pwd)
LABEL=${2:-dev.jahaziel}
PLIST="/Library/LaunchDaemons/${LABEL}.plist"
TEMPLATE="$(dirname "$0")/dev.jahaziel.plist.template"

if [ ! -f "$JAR_DIR/jahaziel.jar" ]; then
    echo "missing $JAR_DIR/jahaziel.jar" >&2
    exit 1
fi
if [ ! -f "$TEMPLATE" ]; then
    echo "missing $TEMPLATE" >&2
    exit 1
fi

mkdir -p "$JAR_DIR/data/logs" "$JAR_DIR/data/tmp"
sed "s|__JAR_DIR__|$JAR_DIR|g" "$TEMPLATE" > "$PLIST"
chmod 644 "$PLIST"

if launchctl print "system/$LABEL" >/dev/null 2>&1; then
    launchctl bootout "system/$LABEL" || true
fi
launchctl bootstrap system "$PLIST"
echo "registered $LABEL from $JAR_DIR"
