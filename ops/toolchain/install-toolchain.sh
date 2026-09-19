#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

# Installs every toolchain the Jahaziel server needs: Java 25, Maven,
# Go, Node 20, Yarn, Docker, PostgreSQL 16, protoc, minisign, Caddy,
# and the image-building utilities. Target: Ubuntu 24.04 x86_64.
#
# Idempotent: each component is skipped when a compatible version is
# already on PATH (major version match, see the checks below).
# Nothing here formats, partitions, or mounts disks.
#
# Usage: sudo ops/toolchain/install-toolchain.sh [--dry-run]

set -euo pipefail

DRY_RUN=0
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=1
fi
if [ "${DRY_RUN_ENV:-0}" = "1" ]; then
  DRY_RUN=1
fi

MAVEN_VERSION="3.9.9"
NODE_VERSION="20.18.0"
YARN_VERSION="1.22.22"
GO_PLUGINS_VERSION="1.36.0"
GO_GRPC_PLUGIN_VERSION="1.5.1"
INSTALL_ROOT="/opt"

log() {
  echo "[toolchain] $*"
}

fail() {
  echo "[toolchain] ERROR: $*" >&2
  exit 1
}

run() {
  if [ "$DRY_RUN" = "1" ]; then
    echo "+ $*"
    # Drain a piped producer (e.g. echo ... | run ... tee): without a
    # reader the producer takes SIGPIPE, which fails the script under
    # pipefail. Skipped on a terminal so interactive use never blocks.
    if [ ! -t 0 ]; then
      cat >/dev/null
    fi
  else
    "$@"
  fi
}

have() {
  command -v "$1" >/dev/null 2>&1
}

if ! have apt-get; then
  fail "apt-get not found; this script targets Ubuntu 24.04"
fi

SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  have sudo || fail "run as root or install sudo first"
  SUDO="sudo"
fi

install_apt_base() {
  log "APT base packages (apt itself skips what is already newest)"
  run $SUDO apt-get update
  run $SUDO apt-get install -y \
    build-essential pkg-config git curl jq unzip xz-utils zstd \
    python3 lsb-release debootstrap e2fsprogs nftables shellcheck socat
}

install_users() {
  for user in jahaziel-control-plane jahaziel-node-agent jahaziel-gateway; do
    if id "$user" >/dev/null 2>&1; then
      log "user $user already present, skipping"
    else
      log "creating service user $user"
      run $SUDO useradd -r -s /usr/sbin/nologin "$user"
    fi
  done
  run $SUDO mkdir -p /var/log/jahaziel /var/lib/jahaziel
  run $SUDO chown jahaziel-node-agent:jahaziel-node-agent /var/lib/jahaziel
  run $SUDO chmod 0710 /var/lib/jahaziel
}

install_postgres() {
  if have psql && psql --version 2>&1 | grep -q " 16\."; then
    log "PostgreSQL 16 already present, skipping"
    return
  fi
  log "installing PostgreSQL 16"
  run $SUDO apt-get install -y postgresql-16
}

install_protoc() {
  if have protoc; then
    log "protoc already present, skipping"
    return
  fi
  log "installing protoc"
  run $SUDO apt-get install -y protobuf-compiler
}

install_minisign() {
  if have minisign; then
    log "minisign already present, skipping"
    return
  fi
  log "installing minisign"
  run $SUDO apt-get install -y minisign
}

install_java() {
  if have java && java -version 2>&1 | head -1 | grep -q '"25'; then
    log "Java 25 already present, skipping"
    return
  fi
  log "installing Temurin JDK 25 to $INSTALL_ROOT/java-25"
  run $SUDO mkdir -p "$INSTALL_ROOT"
  run curl -fsSL \
    "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    -o /tmp/jahaziel-jdk25.tar.gz
  run $SUDO mkdir -p "$INSTALL_ROOT/java-25"
  run $SUDO tar -xzf /tmp/jahaziel-jdk25.tar.gz -C "$INSTALL_ROOT/java-25" --strip-components=1
  run rm -f /tmp/jahaziel-jdk25.tar.gz
}

install_maven() {
  if have mvn && mvn -version 2>&1 | head -1 | grep -q "3\.9"; then
    log "Maven 3.9 already present, skipping"
    return
  fi
  log "installing Maven $MAVEN_VERSION to $INSTALL_ROOT/maven"
  run curl -fsSL \
    "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" \
    -o /tmp/jahaziel-maven.tar.gz
  run $SUDO mkdir -p "$INSTALL_ROOT/maven"
  run $SUDO tar -xzf /tmp/jahaziel-maven.tar.gz -C "$INSTALL_ROOT/maven" --strip-components=1
  run rm -f /tmp/jahaziel-maven.tar.gz
}

install_go() {
  if have go && go version 2>&1 | grep -q "go1\.24"; then
    log "Go 1.24 already present, skipping"
    return
  fi
  have jq || fail "jq is required to resolve the Go download"
  log "resolving latest Go 1.24 download"
  GO_FILE="$(curl -fsSL 'https://go.dev/dl/?mode=json' \
    | jq -r '[.[] | select(.version | startswith("go1.24")) | .files[] | select(.os=="linux" and .arch=="amd64" and .kind=="archive")][0].filename')"
  [ -n "$GO_FILE" ] && [ "$GO_FILE" != "null" ] || fail "could not resolve Go download"
  log "installing $GO_FILE to /usr/local/go"
  run curl -fsSL "https://go.dev/dl/$GO_FILE" -o /tmp/jahaziel-go.tar.gz
  run $SUDO rm -rf /usr/local/go
  run $SUDO tar -xzf /tmp/jahaziel-go.tar.gz -C /usr/local
  run rm -f /tmp/jahaziel-go.tar.gz
}

install_node() {
  if have node && node --version 2>&1 | grep -q "^v20"; then
    log "Node 20 already present, skipping"
  else
    log "installing Node $NODE_VERSION to $INSTALL_ROOT/node-20"
    run curl -fsSL "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-x64.tar.xz" \
      -o /tmp/jahaziel-node.tar.xz
    run $SUDO mkdir -p "$INSTALL_ROOT/node-20"
    run $SUDO tar -xJf /tmp/jahaziel-node.tar.xz -C "$INSTALL_ROOT/node-20" --strip-components=1
    run rm -f /tmp/jahaziel-node.tar.xz
  fi
  if have yarn && yarn --version 2>&1 | grep -q "^1\."; then
    log "Yarn 1 already present, skipping"
    return
  fi
  log "installing Yarn $YARN_VERSION"
  NPM="$(command -v npm || true)"
  [ -n "$NPM" ] || NPM="$INSTALL_ROOT/node-20/bin/npm"
  run "$NPM" install -g "yarn@$YARN_VERSION"
}

install_docker() {
  if have docker; then
    log "Docker already present, skipping"
    return
  fi
  log "installing Docker Engine from the official repository"
  run $SUDO install -m 0755 -d /etc/apt/keyrings
  run curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /tmp/jahaziel-docker.asc
  run $SUDO cp /tmp/jahaziel-docker.asc /etc/apt/keyrings/docker.asc
  run $SUDO chmod a+r /etc/apt/keyrings/docker.asc
  run rm -f /tmp/jahaziel-docker.asc
  UBUNTU_CODENAME="$(run lsb_release -cs 2>/dev/null || echo noble)"
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $UBUNTU_CODENAME stable" \
    | run $SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
  run $SUDO apt-get update
  run $SUDO apt-get install -y \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
}

install_caddy() {
  if have caddy; then
    log "Caddy already present, skipping"
    return
  fi
  log "installing Caddy from the official repository"
  run curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/gpg.key -o /tmp/jahaziel-caddy.asc
  run $SUDO cp /tmp/jahaziel-caddy.asc /etc/apt/keyrings/caddy.asc
  run rm -f /tmp/jahaziel-caddy.asc
  echo "deb [signed-by=/etc/apt/keyrings/caddy.asc] https://dl.cloudsmith.io/public/caddy/stable/deb/debian any-version main" \
    | run $SUDO tee /etc/apt/sources.list.d/caddy.list >/dev/null
  run $SUDO apt-get update
  run $SUDO apt-get install -y caddy
}

install_go_plugins() {
  have go || fail "Go is required for the protobuf plugins"
  if have protoc-gen-go; then
    log "protoc-gen-go already present, skipping"
  else
    log "installing protoc-gen-go v$GO_PLUGINS_VERSION"
    run go install "google.golang.org/protobuf/cmd/protoc-gen-go@v$GO_PLUGINS_VERSION"
  fi
  if have protoc-gen-go-grpc; then
    log "protoc-gen-go-grpc already present, skipping"
  else
    log "installing protoc-gen-go-grpc v$GO_GRPC_PLUGIN_VERSION"
    run go install "google.golang.org/grpc/cmd/protoc-gen-go-grpc@v$GO_GRPC_PLUGIN_VERSION"
  fi
}

write_profile() {
  log "writing /etc/profile.d/jahaziel-toolchain.sh"
  PROFILE='export JAVA_HOME=/opt/java-25
export PATH="/opt/java-25/bin:/opt/maven/bin:/usr/local/go/bin:/opt/node-20/bin:$HOME/go/bin:$PATH"'
  echo "$PROFILE" | run $SUDO tee /etc/profile.d/jahaziel-toolchain.sh >/dev/null
}

report() {
  log "toolchain versions:"
  for tool in "java -version" "mvn -version" "go version" "node --version" "yarn --version" \
    "docker --version" "psql --version" "protoc --version" "minisign -h" "caddy version" \
    "shellcheck --version" "debootstrap --version"; do
    # shellcheck disable=SC2086
    echo "--- $tool"; $tool 2>&1 | head -2 || true
  done
}

install_apt_base
install_users
install_postgres
install_protoc
install_minisign
install_java
install_maven
install_go
export PATH="/opt/java-25/bin:/opt/maven/bin:/usr/local/go/bin:/opt/node-20/bin:$HOME/go/bin:$PATH"
install_node
install_docker
install_caddy
install_go_plugins
write_profile
if [ "$DRY_RUN" = "0" ]; then
  # shellcheck disable=SC1091
  report
else
  log "dry run: no changes made"
fi
