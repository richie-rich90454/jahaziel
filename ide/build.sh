#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

# Upstream tracks ~98 files under extensions/copilot/test/simulation/cache via Git LFS.
# The smudge filter blocks on the LFS endpoint (and hangs forever when it is unreachable),
# and none of those fixtures are needed to build the web client or the server.
export GIT_LFS_SKIP_SMUDGE=1

cd "$(dirname "$0")"
TAG="$(cat upstream-version.txt)"

node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
node_minor="$(node -p 'process.versions.node.split(".")[1]' 2>/dev/null || echo 0)"
if [ "$node_major" -ne 24 ] || [ "$node_minor" -lt 18 ]; then
  echo "Node.js 24.18.0 or newer is required (upstream .nvmrc pins 24.18.0 and build/npm/preinstall.ts enforces it); found $(node --version 2>/dev/null || echo none)" >&2
  exit 1
fi
command -v npm >/dev/null || { echo "npm is required" >&2; exit 1; }
command -v zstd >/dev/null || { echo "zstd is required for packaging" >&2; exit 1; }

# This target builds a linux-x64 server. Run it on Linux (or a linux CI agent):
# the native modules it compiles are platform binaries, they cannot be produced
# by cross-compiling from macOS or Windows.
if [ "$(uname -s)" != "Linux" ]; then
  echo "vscode-reh-web-linux-x64 must be built on Linux; found $(uname -s)" >&2
  exit 1
fi

UPSTREAM_URL="${JAHAZIEL_VSCODE_URL:-git@github.com:microsoft/vscode.git}"

if [ ! -d upstream/.git ]; then
  echo "cloning microsoft/vscode at $TAG from $UPSTREAM_URL"
  git clone -c core.autocrlf=false --depth 1 --branch "$TAG" "$UPSTREAM_URL" upstream
fi

if ! git -C upstream rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  git -C upstream fetch --depth 1 origin "refs/tags/$TAG:refs/tags/$TAG"
fi
git -C upstream checkout "$TAG"
git -C upstream reset --hard "$TAG"

ALLOW_EMPTY="0006-remove-live-share.patch 0012-server-runtime-config.patch"

for patch in patches/*.patch; do
  if grep -q '^diff --git' "$patch"; then
    echo "Applying $patch"
    git -C upstream apply --3way "../$patch" || exit 1
  elif [[ " $ALLOW_EMPTY " == *" $(basename "$patch") "* ]]; then
    echo "Skipping $patch: intentionally hunk-less (see patch header)"
  else
    echo "ERROR: $patch has no hunks and is not allow-listed" >&2
    exit 1
  fi
done

cd upstream
# Upstream moved off yarn in this line: package-lock.json is the lockfile and
# build/npm/preinstall.ts aborts if it detects yarn.
npm ci
npm run gulp vscode-reh-web-linux-x64-min

cd ..
mkdir -p out
rm -f "out/jahaziel-code-server.tar.zst" "out/SHA256SUMS"
tar --use-compress-program="zstd -19" -cf out/jahaziel-code-server.tar.zst \
  -C . vscode-reh-web-linux-x64
if command -v sha256sum >/dev/null; then
  sha256sum out/jahaziel-code-server.tar.zst > out/SHA256SUMS
else
  shasum -a 256 out/jahaziel-code-server.tar.zst > out/SHA256SUMS
fi

echo "Built out/jahaziel-code-server.tar.zst"
