#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

# Static smoke test for the IDE fork patches. Asserts each patch's
# markers in an upstream checkout (pristine or patched). No network,
# no toolchain beyond grep and test.
#
# Usage: ide/tests/patch-smoke.sh <upstream-dir>

set -euo pipefail

UPSTREAM="${1:?usage: patch-smoke.sh <upstream-dir>}"
PASS=0
FAIL=0

pass() {
  PASS=$((PASS + 1))
}

fail() {
  FAIL=$((FAIL + 1))
  echo "FAIL: $1"
}

check_present() {
  if grep -q -e "$2" "$UPSTREAM/$1" 2>/dev/null; then
    pass
  else
    fail "$3 ($1 lacks $2)"
  fi
}

check_absent() {
  if grep -q -e "$2" "$UPSTREAM/$1" 2>/dev/null; then
    fail "$3 ($1 still has $2)"
  else
    pass
  fi
}

# 0001 product rebrand
check_present product.json '"nameShort": "Jahaziel Code"' 0001-name
check_present product.json '"serverDataFolderName": ".jahaziel-code-server"' 0001-server-data
check_present product.json '"quality": "stable"' 0001-quality
check_present product.json 'github.com/example/jahaziel' 0001-links
check_present product.json '"darwinBundleIdentifier": "com.example.jahaziel-code"' 0001-bundle
check_present product.json '"win32TunnelServiceMutex": "jahaziel-tunnelservice"' 0001-mutex
check_present product.json '"linuxIconName": "jahaziel-code"' 0001-icon

# 0002 runtime strings
check_present src/vs/code/browser/workbench/workbench.ts 'jahaziel-code-reqid' 0002-rebrand
check_absent src/vs/code/browser/workbench/workbench.ts "from 'jahaziel-code'" 0002-imports
check_absent src/vs/code/browser/workbench/workbench.ts 'from "jahaziel-code"' 0002-imports-dq
check_absent src/vs/code/browser/workbench/workbench.ts 'jahaziel-code-remote:' 0002-scheme
check_absent src/vs/code/browser/workbench/workbench.ts '--jahaziel-code-' 0002-cssvar

# 0003 fonts and manifest
check_present resources/server/manifest.json '"short_name": "Jahaziel Code"' 0003-manifest
check_present src/vs/base/browser/fonts.ts 'system-ui, sans-serif' 0003-fonts
check_absent src/vs/workbench/browser/media/style.css 'Segoe WPC' 0003-segoe

# 0004 telemetry pin
check_present product.json '"enableTelemetry": false' 0004-telemetry

# 0005 copilot surfaces
check_absent src/vs/workbench/contrib/chat/browser/chatParticipantContributions.ts 'GitHub Copilot' 0005-name
check_absent src/vs/workbench/contrib/chat/browser/chatParticipantContributions.ts 'ChatCompatibilityNotifier' 0005-notifier
check_absent src/vs/workbench/contrib/chat/browser/chat.contribution.ts 'ChatCompatibilityNotifier' 0005-registration

# 0007 tunnels
if [ -d "$UPSTREAM/extensions/tunnel-forwarding" ]; then
  fail "0007 tunnel-forwarding still present"
else
  pass
fi
check_present product.json '"tunnelApplicationName": "jahaziel-tunnel"' 0007-tunnel-name

# 0008 marketplace
check_present product.json 'https://open-vsx.org/vscode/gallery' 0008-gallery

# 0009 account auth
if [ -d "$UPSTREAM/extensions/microsoft-authentication" ]; then
  fail "0009 microsoft-authentication still present"
else
  pass
fi
if [ -d "$UPSTREAM/extensions/github-authentication" ]; then
  fail "0009 github-authentication still present"
else
  pass
fi
check_absent src/vs/code/browser/workbench/workbench.ts 'github-authentication' 0009-client
check_absent src/vs/server/node/webClientServer.ts 'github-authentication' 0009-server

# 0010 autoupdate
check_absent src/vs/workbench/contrib/update/browser/update.contribution.ts 'SwitchProductQualityContribution' 0010-quality-switch

# 0011 settings sync
check_absent src/vs/workbench/workbench.common.main.ts 'userDataSync/browser/userDataSync.contribution' 0011-sync

# 0013 brand tokens
if [ -f "$UPSTREAM/resources/server/jahaziel-branding.css" ]; then
  pass
else
  fail "0013 branding css missing"
fi
check_present src/vs/code/browser/workbench/workbench.html 'jahaziel-branding.css' 0013-link
check_present build/gulpfile.reh.js 'jahaziel-branding.css' 0013-packaging

# 0014 tablet hardening
check_present src/vs/code/browser/workbench/workbench.html 'Jahaziel tablet hardening' 0014-script
check_present src/vs/code/browser/workbench/workbench.html 'jahaziel-touch' 0014-style

echo "patch smoke: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
