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

# The npm launcher ships as a shell script whose shebang resolves `bash` through PATH. On
# Windows a WSL `bash.exe` stub often shadows the real shell, and npm then dies with
# "No such file or directory". Putting the running shell's own directory first makes
# `env bash` resolve to a real bash. Harmless everywhere else.
shell_dir=$(dirname "$(command -v sh)")
PATH="$shell_dir:$PATH"
export PATH

cd "$(dirname "$0")"
TAG="$(cat upstream-version.txt)"

# Prefer the project-local Node pinned under ide/toolchain, so a build never depends on a
# globally installed toolchain. Fall back to whatever Node is on PATH.
if [ -x toolchain/node/node.exe ]; then
  PATH="$PWD/toolchain/node:$PATH"
elif [ -x toolchain/node/bin/node ]; then
  PATH="$PWD/toolchain/node/bin:$PATH"
fi
export PATH

node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
node_minor="$(node -p 'process.versions.node.split(".")[1]' 2>/dev/null || echo 0)"
if [ "$node_major" -ne 24 ] || [ "$node_minor" -lt 18 ]; then
  echo "Node.js 24.18.0 or newer is required (upstream .nvmrc pins 24.18.0 and build/npm/preinstall.ts enforces it); found $(node --version 2>/dev/null || echo none)" >&2
  exit 1
fi
command -v npm >/dev/null 2>&1 || { echo "npm is required for packaging" >&2; exit 1; }
command -v zstd >/dev/null 2>&1 || { echo "zstd is required for packaging" >&2; exit 1; }

# The REH server compiles native modules for its own platform, so each host builds its own
# variant. The slug matches IdeService.Variant and the tarball name the runtime looks for.
case "$(uname -s)" in
  Linux) reh_os=linux ;;
  Darwin) reh_os=darwin ;;
  MINGW* | MSYS* | CYGWIN* | Windows_NT) reh_os=win32 ;;
  *) echo "unsupported build host: $(uname -s); build each REH variant on its own platform" >&2; exit 1 ;;
esac
case "$(node -p 'process.arch')" in
  x64) reh_arch=x64 ;;
  arm64) reh_arch=arm64 ;;
  *) echo "unsupported architecture: $(node -p 'process.arch')" >&2; exit 1 ;;
esac
slug="$reh_os-$reh_arch"
package="vscode-reh-web-$slug"
tarball="jahaziel-code-server-$slug.tar.zst"

UPSTREAM_URL="${JAHAZIEL_VSCODE_URL:-git@github.com:microsoft/vscode.git}"

if [ ! -d upstream/.git ]; then
  echo "cloning microsoft/vscode at $TAG from $UPSTREAM_URL"
  git clone -c core.autocrlf=false --depth 1 --branch "$TAG" "$UPSTREAM_URL" upstream
fi

if ! git -C upstream rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  git -C upstream fetch --depth 1 origin "refs/tags/$TAG:refs/tags/$TAG"
fi
# Reset before checkout: a previous failed run can leave unmerged index entries,
# and `git checkout` refuses to switch while those are present.
git -C upstream reset --hard "$TAG"
git -C upstream checkout -f "$TAG"
git -C upstream clean -qfd

ALLOW_EMPTY="0005-remove-ai-features.patch 0006-remove-live-share.patch 0012-server-runtime-config.patch"

for patch in patches/*.patch; do
  name=$(basename "$patch")
  if grep -q '^diff --git' "$patch"; then
    echo "Applying $patch"
    git -C upstream apply --3way "../$patch" || exit 1
  else
    case " $ALLOW_EMPTY " in
      *" $name "*) echo "Skipping $patch: intentionally hunk-less (see patch header)" ;;
      *)
        echo "ERROR: $patch has no hunks and is not allow-listed" >&2
        exit 1
        ;;
    esac
  fi
done

cd upstream
if [ ! -d node_modules ]; then
  # Install without lifecycle scripts first: the native modules are compiled separately by
  # ide/scripts/native-build.mjs, which does not need Visual Studio's optional
  # Spectre-mitigated libraries component. A plain `npm ci` fails on hosts without it.
  npm ci --ignore-scripts
  node build/npm/preinstall.ts || true
  node build/npm/postinstall.ts || true
  cd ..
  node scripts/native-build.mjs upstream
  cd upstream
fi
npm run gulp "vscode-reh-web-$slug-min"

cd ..
# The runtime extracts the tarball into data/ide/ and expects the variant slug as the
# top-level directory, so stage the build under that name before packing.
rm -rf "out/$slug"
cp -R "upstream/out/$package" "out/$slug"
mkdir -p out
rm -f "out/$tarball" "out/$tarball.sha256"
tar --use-compress-program="zstd -19" -cf "out/$tarball" -C out "$slug"
rm -rf "out/$slug"

# The runtime verifies each tarball against its own .sha256 sidecar; a missing sidecar
# fails closed, so always write one next to the artifact.
if command -v sha256sum >/dev/null 2>&1; then
  (cd out && sha256sum "$tarball" >"$tarball.sha256")
else
  (cd out && shasum -a 256 "$tarball" >"$tarball.sha256")
fi

echo
echo "Built out/$tarball"
echo "Install it with: cp out/$tarball out/$tarball.sha256 <jar-dir>/data/ide/"
