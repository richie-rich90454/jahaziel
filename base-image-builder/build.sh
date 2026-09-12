#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

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
cp shell/jahaziel-sync.py "$WORK/root/usr/local/bin/jahaziel-sync"
chmod 0755 "$WORK/root/usr/local/bin/jahaziel-sync"
tar --use-compress-program="zstd -19" -xf ../../ide/out/jahaziel-code-server.tar.zst \
  -C "$WORK/root/opt"
mv "$WORK/root/opt/vscode-reh-web-linux-x64" "$WORK/root/opt/jahaziel-code-server"

mkdir -p "$WORK/root/etc/systemd/system"
cp systemd/*.service "$WORK/root/etc/systemd/system/"
systemd-nspawn -D "$WORK/root" useradd -r -m -s /bin/bash jahaziel-guest
systemd-nspawn -D "$WORK/root" systemctl enable jahaziel-guest-agent.service jahaziel-code-server.service jahaziel-sync-push.timer

# Guests boot the shared image read-only, so scratch paths live on tmpfs.
cat >> "$WORK/root/etc/fstab" <<'EOF'
tmpfs /tmp tmpfs defaults,nosuid,nodev 0 0
tmpfs /var/tmp tmpfs defaults,nosuid,nodev 0 0
tmpfs /var/log tmpfs defaults,nosuid,nodev 0 0
EOF

systemd-nspawn -D "$WORK/root" apt-get autoremove -y
systemd-nspawn -D "$WORK/root" apt-get clean
rm -rf "$WORK/root/usr/share/doc" "$WORK/root/usr/share/man"

truncate -s 20G "$WORK/root.img"
mkfs.ext4 -d "$WORK/root" "$WORK/root.img"
zstd -19 -f "$WORK/root.img" -o "$OUT"

sha256sum "$OUT" > "$OUT.sha256"
minisign -S -s "$JAHAZIEL_SIGNING_KEY" -m "$OUT.sha256"
echo "Built $OUT"
