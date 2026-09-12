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

if [ ! -d upstream/.git ]; then
  echo "cloning microsoft/vscode at $TAG"
  git clone --depth 1 --branch "$TAG" https://github.com/microsoft/vscode.git upstream
fi

git -C upstream fetch --tags origin
git -C upstream checkout "$TAG"

for patch in patches/*.patch; do
  if grep -q '^diff --git' "$patch"; then
    echo "Applying $patch"
    git -C upstream apply --3way "$patch" || exit 1
  else
    echo "Skipping $patch: no upstream hunks (feature absent from OSS)"
  fi
done

cd upstream
yarn install --frozen-lockfile
yarn gulp vscode-reh-web-linux-x64-min

cd ..
mkdir -p out
rm -f "out/jahaziel-code-server.tar.zst" "out/jahaziel-code-server.tar.zst.sha256"
tar --use-compress-program="zstd -19" -cf out/jahaziel-code-server.tar.zst \
  -C . vscode-reh-web-linux-x64
sha256sum out/jahaziel-code-server.tar.zst > out/jahaziel-code-server.tar.zst.sha256

echo "Built out/jahaziel-code-server.tar.zst"
