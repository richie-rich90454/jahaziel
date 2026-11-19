#!/bin/sh
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -eu
LABEL=${1:-dev.jahaziel}
PLIST="/Library/LaunchDaemons/${LABEL}.plist"
if launchctl print "system/$LABEL" >/dev/null 2>&1; then
    launchctl bootout "system/$LABEL"
fi
rm -f "$PLIST"
echo "unregistered $LABEL"
