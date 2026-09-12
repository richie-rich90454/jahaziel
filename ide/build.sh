#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

cd "$(dirname "$0")"
TAG="$(cat upstream-version.txt)"

if [ ! -d upstream/.git ]; then
  echo "upstream/ is not a git checkout; add the microsoft/vscode remote first" >&2
  exit 1
fi

git -C upstream fetch --tags origin
git -C upstream checkout "$TAG"

for patch in patches/*.patch; do
  echo "Applying $patch"
  git -C upstream apply --3way "$patch" || exit 1
done

cd upstream
yarn install --frozen-lockfile
yarn gulp vscode-web-min
yarn gulp vscode-reh-web-min

echo "Build finished; package ide/out/ from the gulp output directories"
