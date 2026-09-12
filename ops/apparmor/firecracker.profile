# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
# AppArmor profile for Firecracker. Optional per-node hardening on top of
# the jailer namespaces, seccomp filter, and chroot.
abi <abi/4.0>,

include <tunables/global>

/usr/local/bin/firecracker flags=(attach_disconnected) {
  include <abstractions/base>

  /usr/local/bin/firecracker mr,
  /var/lib/jahaziel/jail/** rwk,
  /var/lib/jahaziel/images/* r,
  /var/run/netns/jahaziel-* r,
  /dev/kvm rw,
  /dev/net/tun rw,
  /sys/fs/cgroup/jahaziel/** rw,

  deny network inet,
  deny network inet6,
  deny /proc/sys/** w,
  deny /sys/** w,
}
