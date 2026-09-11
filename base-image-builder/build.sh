#!/usr/bin/env bash
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#
# Builds the base image: debootstrap, packages, toolchain, guest agent,
# shell integration, IDE server, systemd, network, cleanup, compress, sign.
# Run on the image build host (Debian 12, root, ~30 GB free).
set -euo pipefail

VERSION="${1:-2026.09}"
ROOT="$(dirname "$0")"
WORK="$ROOT/work-$VERSION"
OUT="$ROOT/base-image-$VERSION.ext4.zst"

rm -rf "$WORK"
mkdir -p "$WORK/root"

debootstrap --variant=minbase bookworm "$WORK/root" http://deb.debian.org/debian

systemd-nspawn -D "$WORK/root" apt-get install -y --no-install-recommends \
  $(grep -v '^#' "$ROOT/packages.txt" | grep -v '^$' | tr '\n' ' ')

cp ../../guest-agent/target/release/jahaziel-guest-agent "$WORK/root/usr/local/bin/"
cp shell/jahaziel-shell-integration.sh "$WORK/root/etc/profile.d/"
tar --use-compress-program="zstd -19" -xf ../../ide/out/jahaziel-code-server.tar.zst \
  -C "$WORK/root/opt"

systemd-nspawn -D "$WORK/root" systemctl enable jahaziel-guest-agent.service

systemd-nspawn -D "$WORK/root" apt-get autoremove -y
systemd-nspawn -D "$WORK/root" apt-get clean
rm -rf "$WORK/root/usr/share/doc" "$WORK/root/usr/share/man"

truncate -s 20G "$WORK/root.img"
mkfs.ext4 -d "$WORK/root" "$WORK/root.img"
zstd -19 -f "$WORK/root.img" -o "$OUT"

sha256sum "$OUT" > "$OUT.sha256"
minisign -S -s "$JAHAZIEL_SIGNING_KEY" -m "$OUT.sha256"
echo "Built $OUT"
