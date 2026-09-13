<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Single-server deployment

Fresh Ubuntu 24.04 install, one public IPv4, DNS pointed at the host.

## 1. Base

```bash
sudo ops/toolchain/install-toolchain.sh
systemctl enable --now postgresql
```

Create the database and roles:

```bash
sudo -u postgres psql -c "CREATE DATABASE jahaziel;"
sudo -u postgres psql -c "CREATE ROLE \"jahaziel-control-plane\" LOGIN PASSWORD 'secret';"
sudo -u postgres psql -c "CREATE ROLE \"jahaziel-audit-writer\" LOGIN PASSWORD 'secret';"
```

Create the storage root as plain folders on any filesystem; workspaces
need no dedicated partition, disk image, or filesystem features:

```bash
mkdir -p /var/lib/jahaziel/{workspaces,images,jail,audit,replicas}
chown -R jahaziel-node-agent:jahaziel-node-agent /var/lib/jahaziel
chmod 0710 /var/lib/jahaziel
```

## 2. DNS and TLS

| Record | Type | Value |
|---|---|---|
| `code.example.com` | A | server IPv4 |
| `*.code.example.com` | A | server IPv4 |

Copy `ops/caddy/Caddyfile` to `/etc/caddy/Caddyfile`, set the DNS
provider token, and reload Caddy. Certificates are automatic.

## 3. Firewall

```bash
nft -f ops/nftables/jahaziel.nft
```

## 4. Services

Copy the built jars to `/opt/jahaziel/` under the names the units expect
(the Maven build now produces these names directly):

```bash
cp control-plane/target/control-plane-exec.jar gateway/target/gateway-exec.jar \
  node-agent/target/node-agent-exec.jar /opt/jahaziel/
```

Write `/etc/jahaziel/*.env` (mode 0600) with the database and OIDC settings
the units load via `EnvironmentFile`:

```bash
cat >/etc/jahaziel/control-plane.env <<'EOF'
DB_USER=jahaziel-control-plane
DB_PASSWORD=secret
OIDC_CLIENT_ID=...
OIDC_CLIENT_SECRET=...
OIDC_ISSUER_URI=https://idp.example.com
EOF
chmod 0600 /etc/jahaziel/*.env
```

Service users (`jahaziel-control-plane`, `jahaziel-node-agent`,
`jahaziel-gateway`) are created by `ops/toolchain/install-toolchain.sh`.
Install the units, then enable:

```bash
cp ops/systemd/*.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now jahaziel-control-plane jahaziel-node-agent jahaziel-gateway jahaziel-sync-relay
```

Flyway migrates the schema on control-plane start.

Workspace start/stop in this split topology needs the node-agent mTLS
gRPC transport (planned; starts fail closed until it lands). The fat jar
below runs the full loop today.

## 5. Verify

- `https://code.example.com/actuator/health` returns 200.
- Log in, create a workspace, open the IDE, run a command.
- `https://<user>-8080.code.example.com` serves the workspace port.

## 6. Backups

Install `ops/backup/backup.sh` as a daily cron job plus continuous WAL
archiving. Run `ops/backup/restore-drill.sh` quarterly and record the
result.

## 7. Fat jar (portable single file)

Build once on any OS with Java 25 and Maven:

```bash
./mvnw.cmd -B -DskipTests package   # Windows
./mvnw -B -DskipTests package       # Linux/macOS
```

Copy `dist/target/jahaziel.jar` plus the launcher for the target OS
(`jahaziel.sh`, `jahaziel.ps1`, `jahaziel.bat`) into one folder on the
machine. That folder is the install: first run creates `data/` beside
the jar (workspaces, images, jail, audit, sync, keys, logs, and
`jahaziel.db`, a SQLite file that is created once and never cleared).

```bash
bash jahaziel.sh                    # --run=all: API :8080, gateway :8081
java -jar jahaziel.jar              # bare jar prints help and runs nothing
java -jar jahaziel.jar --run=all --port=8080 --gateway-port=8081 --data-dir=/srv/jahaziel-data
```

Only Java 25 is required on the target; no Docker, no Postgres, no
extra installs. Workspaces run on the zero-install local backend with
real folders (Firecracker remains available via the split topology on
Linux). Windows Server and macOS run the same jar; for auto-start use
Task Scheduler (`jahaziel.ps1`) or launchd (`jahaziel.sh`).
