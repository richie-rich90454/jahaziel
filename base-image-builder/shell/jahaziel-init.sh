#!/bin/bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

set -uo pipefail

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export PATH

SERVICES_DIR=/etc/jahaziel/services
SUPERVISOR_JAR=/opt/jahaziel/jahaziel.jar
SUPERVISOR_PID=""

log() { echo "jahaziel-init: $*" >/dev/ttyS0 2>/dev/null || echo "jahaziel-init: $*"; }

mount_pseudo_filesystems() {
  mount -t proc proc /proc 2>/dev/null
  mount -t sysfs sysfs /sys 2>/dev/null
  mount -t devtmpfs devtmpfs /dev 2>/dev/null || mount -t tmpfs tmpfs /dev
  mkdir -p /dev/pts /dev/shm
  mount -t devpts devpts /dev/pts 2>/dev/null
  mount -t tmpfs tmpfs /dev/shm 2>/dev/null
  mount -t tmpfs tmpfs /run 2>/dev/null
}

# Guests boot the shared image read-only, so every writable path is tmpfs.
mount_scratch_space() {
  for dir in /tmp /var/tmp /var/log; do
    mkdir -p "$dir"
    mount -t tmpfs -o nosuid,nodev tmpfs "$dir" 2>/dev/null
  done
}

start_time_sync() {
  chronyd -q 2>/dev/null || chronyd 2>/dev/null || log "time sync unavailable"
}

# PID 1 must reap orphans, or the guest leaks zombies until it runs out of pids.
reap_orphans() {
  while true; do
    wait -n 2>/dev/null
    [[ -n "$SUPERVISOR_PID" ]] || continue
    if ! kill -0 "$SUPERVISOR_PID" 2>/dev/null; then
      log "supervisor exited; shutting down"
      shutdown_now
    fi
  done
}

shutdown_now() {
  if [[ -n "$SUPERVISOR_PID" ]] && kill -0 "$SUPERVISOR_PID" 2>/dev/null; then
    kill -TERM "$SUPERVISOR_PID" 2>/dev/null
    for _ in $(seq 1 100); do
      kill -0 "$SUPERVISOR_PID" 2>/dev/null || break
      sleep 0.2
    done
    kill -KILL "$SUPERVISOR_PID" 2>/dev/null
  fi
  sync
  # `reboot` used to come from systemd-sysv. busybox provides it now; the kernel
  # sysrq trigger and exiting PID 1 are the fallbacks.
  busybox reboot -f 2>/dev/null \
    || { echo b > /proc/sysrq-trigger 2>/dev/null; } \
    || exit 0
}

trap shutdown_now SIGTERM SIGINT SIGUSR1

mount_pseudo_filesystems
mount_scratch_space
hostname -F /etc/hostname 2>/dev/null
start_time_sync

log "starting supervised services from $SERVICES_DIR"
java -jar "$SUPERVISOR_JAR" --run=supervisor --services-dir "$SERVICES_DIR" &
SUPERVISOR_PID=$!

reap_orphans
