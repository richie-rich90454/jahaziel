#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

VERSION="${1:-2026.09}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
WORK="$ROOT/work-$VERSION"
OUT="$ROOT/base-image-$VERSION.ext4.zst"

: "${JAHAZIEL_SIGNING_KEY:?set JAHAZIEL_SIGNING_KEY to the signing key path}"

rm -rf "$WORK"
mkdir -p "$WORK/root"

debootstrap --variant=minbase bookworm "$WORK/root" http://deb.debian.org/debian

# No systemd: chroot with the pseudo filesystems bound in replaces systemd-nspawn.
CHROOT_MOUNTS="proc sys dev dev/pts"
mount_chroot() {
  for m in $CHROOT_MOUNTS; do mkdir -p "$WORK/root/$m"; mount --bind "/$m" "$WORK/root/$m"; done
}
umount_chroot() {
  for m in dev/pts dev sys proc; do umount -l "$WORK/root/$m" 2>/dev/null || true; done
}
trap umount_chroot EXIT
mount_chroot
in_image() { chroot "$WORK/root" "$@"; }

in_image apt-get install -y --no-install-recommends \
  $(grep -v '^#' "$ROOT/packages.txt" | grep -v '^$' | tr '\n' ' ')

(cd "$ROOT/../guest-agent" && go build -o "$WORK/root/usr/local/bin/jahaziel-guest-agent" ./...)
cp shell/jahaziel-shell-integration.sh "$WORK/root/etc/profile.d/"
cp shell/jahaziel-sync.py "$WORK/root/usr/local/bin/jahaziel-sync"
cp shell/jahaziel-init.sh "$WORK/root/usr/local/bin/jahaziel-init"
chmod 0755 "$WORK/root/usr/local/bin/jahaziel-sync" "$WORK/root/usr/local/bin/jahaziel-init"
tar --use-compress-program="zstd -19" -xf ../../ide/out/jahaziel-code-server.tar.zst \
  -C "$WORK/root/opt"
mv "$WORK/root/opt/vscode-reh-web-linux-x64" "$WORK/root/opt/jahaziel-code-server"

# The supervisor is our own process manager, so the fat jar ships in the image.
mkdir -p "$WORK/root/opt/jahaziel" "$WORK/root/etc/jahaziel/services"
cp "$ROOT/../dist/target/jahaziel.jar" "$WORK/root/opt/jahaziel/jahaziel.jar"
cp "$ROOT/../ops/services/"*.service "$WORK/root/etc/jahaziel/services/"
cp "$ROOT/../ops/services/"*.timer "$WORK/root/etc/jahaziel/services/" 2>/dev/null || true
in_image useradd -r -m -s /bin/bash jahaziel-guest

# Boot is `init=/usr/local/bin/jahaziel-init` in the kernel command line; it mounts
# the pseudo filesystems and the tmpfs scratch paths itself (see the init script).

in_image apt-get autoremove -y
in_image apt-get clean
rm -rf "$WORK/root/usr/share/doc" "$WORK/root/usr/share/man"

truncate -s 20G "$WORK/root.img"
mkfs.ext4 -d "$WORK/root" "$WORK/root.img"
zstd -19 -f "$WORK/root.img" -o "$OUT"

sha256sum "$OUT" > "$OUT.sha256"
minisign -S -s "$JAHAZIEL_SIGNING_KEY" -m "$OUT.sha256"
echo "Built $OUT"
