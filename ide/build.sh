#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

cd "$(dirname "$0")"
TAG="$(cat upstream-version.txt)"

command -v node >/dev/null || { echo "node 20+ is required; run ops/toolchain/install-toolchain.sh" >&2; exit 1; }
command -v yarn >/dev/null || { echo "yarn is required; run ops/toolchain/install-toolchain.sh" >&2; exit 1; }
command -v zstd >/dev/null || { echo "zstd is required for packaging" >&2; exit 1; }

if [ ! -d upstream/.git ]; then
  echo "cloning microsoft/vscode at $TAG"
  git clone -c core.autocrlf=false --depth 1 --branch "$TAG" https://github.com/microsoft/vscode.git upstream
fi

if ! git -C upstream rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  git -C upstream fetch --depth 1 origin "tag $TAG"
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
yarn install --frozen-lockfile
yarn gulp vscode-reh-web-linux-x64-min

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
