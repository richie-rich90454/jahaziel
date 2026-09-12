<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Single-server deployment

Fresh Debian 12 install, one public IPv4, DNS pointed at the host.

## 1. Base

```bash
apt-get update && apt-get install -y openjdk-25-jre postgresql-16 caddy btrfs-progs nftables
systemctl enable --now postgresql
```

Create the database and roles:

```bash
sudo -u postgres psql -c "CREATE DATABASE jahaziel;"
sudo -u postgres psql -c "CREATE ROLE \"jahaziel-control-plane\" LOGIN PASSWORD 'secret';"
sudo -u postgres psql -c "CREATE ROLE \"jahaziel-audit-writer\" LOGIN PASSWORD 'secret';"
```

Format and mount the data volume with the platform options:

```bash
mkfs.btrfs -L jahaziel /dev/sda1
echo '/dev/sda1 /var/lib/jahaziel btrfs compress=zstd:1,noatime,ssd,space_cache=v2,autodefrag 0 0' >> /etc/fstab
mount /var/lib/jahaziel
btrfs quota enable /var/lib/jahaziel
```

## 2. DNS and TLS

| Record | Type | Value |
|---|---|---|
| `code.jahaziel.dev` | A | server IPv4 |
| `*.code.jahaziel.dev` | A | server IPv4 |

Copy `ops/caddy/Caddyfile` to `/etc/caddy/Caddyfile`, set the DNS
provider token, and reload Caddy. Certificates are automatic.

## 3. Firewall

```bash
nft -f ops/nftables/jahaziel.nft
```

## 4. Services

Install the jars to `/opt/jahaziel/`, secrets to `/etc/jahaziel/*.env`
(mode 0600), configs to `/etc/jahaziel/*.yml`, then enable:

```bash
cp ops/systemd/*.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now jahaziel-control-plane jahaziel-node-agent jahaziel-gateway
```

Flyway migrates the schema on control-plane start.

## 5. Verify

- `https://code.jahaziel.dev/actuator/health` returns 200.
- Log in, create a workspace, open the IDE, run a command.
- `https://<user>-8080.code.jahaziel.dev` serves the workspace port.

## 6. Backups

Install `ops/backup/backup.sh` as a daily cron job plus continuous WAL
archiving. Run `ops/backup/restore-drill.sh` quarterly and record the
result.
