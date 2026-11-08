<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Deploy Jahaziel on a Linux server

This guide installs one Jahaziel jar as a systemd service on Ubuntu 26.04 LTS (the reference host) or another supported Linux x64 or arm64 host, stores its files in `/var/lib/jahaziel`, and serves it through Caddy over HTTPS. The browser workbench is designed first for iPad Safari, Chrome, and Edge, and also supports desktop browsers.

Jahaziel is a remote source IDE for trusted users. Workspaces have separate homes, but terminals, tasks, and Git commands run as native processes under the same Linux service account. Do not expose this deployment to mutually untrusted users.

## Deployment shape

| Component | Value |
|---|---|
| Server | Linux x86_64 or arm64 |
| Java | Runtime 25 or newer |
| Application | One executable jar |
| Application port | `11451`, bound to loopback by the supplied systemd unit |
| Public access | Caddy on ports 80 and 443, with separate IDE and preview hostnames |
| Persistent app files | `/var/lib/jahaziel/.env` and `/var/lib/jahaziel/data/` |
| Login | Existing compatible DevConnect/Basis identity provider |

Use a DNS name you control, such as `code.example.com`. The preview host is `preview.code.example.com`. Replace both examples with your real names throughout this guide.

## 1. Prepare the Linux host

Use Ubuntu 26.04 LTS on Linux x64 or arm64 for the documented reference deployment. Other supported Linux distributions can use the same service layout. Provide these tools through your distribution's normal administration process:

- Java runtime 25 or newer.
- `curl`, used below to fetch the matching systemd unit and to check HTTPS endpoints.
- `git`, used by the workbench Git panel and checked by `doctor`.
- `tar`, used by the backup and restore commands.
- `/bin/bash` or `/bin/sh`, plus `setsid` from `util-linux`, for terminal and task process management.

Jahaziel does not install these tools. Node.js, Python, compilers, and other project runtimes are needed only for tasks or language packs that use them. See [templates](../guide/templates.md) and [language diagnostics](../guide/using-jahaziel.md#language-diagnostics).

Create DNS records before enabling HTTPS:

1. Point `code.example.com` to the server's public IPv4 address and, if used, IPv6 address.
2. Point `preview.code.example.com` to the same addresses. This is one exact preview hostname; wildcard DNS is not needed.
3. Allow inbound SSH for administration and ports 80 and 443 for HTTP/HTTPS. Keep port `11451` private; the included unit binds it to `127.0.0.1`.

The server must reach the configured identity provider. If users explicitly install language packs or per-user coding tools, allow outbound HTTPS access to their pinned catalog sources.

## 2. Download the jar for your server

The [Linux build workflow](https://github.com/richie-rich90454/jahaziel/actions/workflows/linux.yml) uploads `jahaziel-linux-x64` and `jahaziel-linux-arm64` artifacts after it builds and smoke-tests the packaged application.

1. Open the workflow and select a successful run for the commit you intend to deploy.
2. Download the artifact matching the server architecture.
3. Extract it and confirm it contains `jahaziel.jar`.

To build from source, use JDK 25, Node.js 24 or newer, and pnpm 12.14.0:

~~~bash
git clone https://github.com/richie-rich90454/jahaziel.git
cd jahaziel
pnpm --dir web install --frozen-lockfile
pnpm --dir web run build
./mvnw -B verify -Psmoke '-Dtest=!GatewayRelayFilterTest'
~~~

The Maven command matches the current Linux CI verification profile, which excludes `GatewayRelayFilterTest`. These commands use the locked web dependencies and the Maven wrapper. A normal build does not install a system toolchain or bundle language packs. Prefer the workflow artifact for a server install unless you need to build a specific source revision.

## 3. Create the service account and install the jar

Create a dedicated non-login account and install the jar in a directory that account can write. Jahaziel's `doctor`, backup, and offline upgrade commands write files beside the jar.

~~~bash
sudo useradd --system --create-home --home-dir /var/lib/jahaziel --shell /usr/sbin/nologin jahaziel
sudo install --directory --owner=jahaziel --group=jahaziel --mode=0750 /var/lib/jahaziel
sudo install --owner=jahaziel --group=jahaziel --mode=0640 ./jahaziel.jar /var/lib/jahaziel/jahaziel.jar
~~~

If the account already exists, skip `useradd`. Keep any existing home data and confirm that `jahaziel` owns `/var/lib/jahaziel` before continuing.

Check Java and initialize the install as the service account:

~~~bash
/usr/bin/java -version
sudo -u jahaziel -- sh -c 'cd /var/lib/jahaziel && /usr/bin/java -jar jahaziel.jar init'
~~~

If Java is installed somewhere other than `/usr/bin/java`, use the path returned by `command -v java` in the commands and service unit below. `init` writes `.env` beside the jar and creates `data/`.

## 4. Configure real sign-in

Register a confidential client with your compatible DevConnect/Basis identity provider. Set its callback URL to this exact value:

~~~text
https://code.example.com/api/oauth/callback
~~~

Edit the generated file:

~~~bash
sudoedit /var/lib/jahaziel/.env
~~~

Set the five `BASIS_*` values from your identity provider and set the public domain and port:

~~~dotenv
BASIS_AUTH_ISSUER=https://idp.example.com
BASIS_REDIRECT_URI=https://code.example.com/api/oauth/callback
BASIS_CLIENT_ID=your-client-id
BASIS_CLIENT_SECRET=your-client-secret
BASIS_RESOURCE=your-registered-resource
PORT=11451
JAHAZIEL_DOMAIN=code.example.com
JAHAZIEL_LOG_LEVEL=INFO
JAHAZIEL_BACKEND=local
~~~

Replace every example value. The redirect URL must match the provider's registered callback exactly. `BASIS_RESOURCE` must match the resource or audience configured with the provider. Keep `.env` private because it contains a client secret.

~~~bash
sudo chown jahaziel:jahaziel /var/lib/jahaziel/.env
sudo chmod 0600 /var/lib/jahaziel/.env
~~~

Do not run the service with `--demo`. Demo mode bypasses real sign-in and is restricted to loopback.

## 5. Run the preflight check

Run `doctor` as the service account before starting the service:

~~~bash
sudo -u jahaziel -- sh -c 'cd /var/lib/jahaziel && /usr/bin/java -jar jahaziel.jar doctor'
~~~

Fix every failed check before continuing. `doctor` verifies Java, supported host architecture, writable paths, `.env`, the application port, SQLite storage, optional language-tool requirements, process state, and Git availability.

## 6. Install the systemd unit

The repository includes a single-jar unit at [ops/services/jahaziel.service](https://github.com/richie-rich90454/jahaziel/blob/main/ops/services/jahaziel.service). It runs as `jahaziel`, binds the app to loopback, and allows writes only under `/var/lib/jahaziel`. If you downloaded only the jar artifact, fetch the unit from the same commit as the jar. Find that commit SHA on the workflow run page and replace the value below:

~~~bash
SERVICE_COMMIT=replace_with_workflow_commit_sha
curl --fail --location --output /tmp/jahaziel.service "https://raw.githubusercontent.com/richie-rich90454/jahaziel/${SERVICE_COMMIT}/ops/services/jahaziel.service"
sudo install --owner=root --group=root --mode=0644 /tmp/jahaziel.service /etc/systemd/system/jahaziel.service
sudo rm -- /tmp/jahaziel.service
sudo systemctl daemon-reload
sudo systemctl enable --now jahaziel
sudo systemctl status jahaziel --no-pager
~~~

Read service logs with:

~~~bash
sudo journalctl --unit=jahaziel --since=today --no-pager
~~~

The service runs `doctor` before each start. If it fails, fix the reported check and inspect the journal before restarting.

## 7. Configure Caddy and HTTPS

Install Caddy using its [official instructions](https://caddyserver.com/docs/install) for your Linux distribution. Add both exact hosts to `/etc/caddy/Caddyfile`. Replace the example names in the included [Caddy configuration](../../ops/caddy/Caddyfile) and its content security policy with your own hostnames:

~~~caddyfile
code.example.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:11451
    header {
        X-Frame-Options "DENY"
        Content-Security-Policy "default-src 'self'; frame-src 'self' https://preview.code.example.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
    }
}

preview.code.example.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:11451
    header {
        -X-Frame-Options
        Content-Security-Policy "frame-ancestors https://code.example.com"
    }
}
~~~

Make sure the hostname's DNS resolves to this server, then validate and reload Caddy:

~~~bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
~~~

Caddy obtains and renews ordinary HTTPS certificates for both exact hostnames using built-in ACME challenge support. No wildcard DNS, DNS provider module, API token, or wildcard certificate is required. Its reverse proxy supports terminal, language-service, debugger, preview, and hot-reload WebSocket connections. Do not add a separate public route to port `11451`.

Keep `JAHAZIEL_DOMAIN` set to the base host (`code.example.com`), without `https://`. See the [port and preview reference](../reference/ports.md) for workspace preview behavior.

## 8. Verify the deployment

Check the local and public health endpoints:

~~~bash
curl --fail --show-error http://127.0.0.1:11451/actuator/health
curl --fail --show-error https://code.example.com/actuator/health
~~~

Then complete a browser smoke check:

1. Open `https://code.example.com` and sign in through the configured identity provider.
2. Create an empty workspace and confirm it becomes ready.
3. Create and save a text file, then reopen it.
4. Open a terminal and confirm its working directory belongs to that workspace.
5. Run a task only if its project runtime is installed on the host.
6. Start a preview task, confirm it is reachable at `https://preview.code.example.com/{workspaceId}/{port}/`, and check both embedded and new-tab preview opening.
7. Restart Jahaziel and verify the workspace and saved file remain.

Node.js and Python starter tasks need their matching host runtimes. The application jar does not contain them.

## 9. Back up and restore

Stop Jahaziel before making a consistent backup:

~~~bash
sudo systemctl stop jahaziel
sudo -u jahaziel -- sh -c 'umask 077; exec /usr/bin/java -jar /var/lib/jahaziel/jahaziel.jar backup'
~~~

The command prints the archive path and SHA-256 digest. The archive contains `.env` and `data/`, including the SQLite database, workspace files, keys, logs, and selected language-pack state. Treat it as secret material. Copy the printed archive to protected off-host storage and apply your retention policy. For example, after replacing the host and directory with your backup target:

~~~bash
scp /var/lib/jahaziel/jahaziel-backup-TIMESTAMP.tar.gz backupuser@backup.example.com:/srv/backups/jahaziel/
~~~

Backups contain a full copy of workspace data, so account for their size. Then restart the service:

~~~bash
sudo systemctl start jahaziel
~~~

To restore an archive, first stop the service. The `restore` command extracts the archive over the install directory; it does not remove existing files that are absent from the archive. Preserve the current configuration and data in a uniquely named directory so the restored `data/` tree starts clean:

~~~bash
set -euo pipefail
sudo systemctl stop jahaziel
restore_hold="/var/lib/jahaziel/pre-restore-$(date +%Y%m%d-%H%M%S)"
sudo install --directory --owner=jahaziel --group=jahaziel --mode=0700 "$restore_hold"
sudo mv /var/lib/jahaziel/.env "$restore_hold/.env"
sudo mv /var/lib/jahaziel/data "$restore_hold/data"
sudo install --owner=jahaziel --group=jahaziel --mode=0600 /path/to/jahaziel-backup.tar.gz /var/lib/jahaziel/restore.tar.gz
sudo -u jahaziel -- /usr/bin/java -jar /var/lib/jahaziel/jahaziel.jar restore /var/lib/jahaziel/restore.tar.gz
sudo systemctl start jahaziel
~~~

The commands stop at the first failure, so a failed restore does not remove the archive or start Jahaziel against partial data. Verify sign-in, workspace contents, and saved files before removing the temporary archive copy. Keep or archive the `pre-restore-*` directory until you no longer need the previous state for rollback. After verification, remove the temporary archive copy:

~~~bash
sudo rm -- /var/lib/jahaziel/restore.tar.gz
~~~

Keep another protected copy of the current state if you need to roll back the restore. See the [backup operations guide](../operations/backup.md).

## 10. Upgrade safely

1. Download the jar artifact for the same Linux architecture.
2. Stop Jahaziel and run the backup command above. Leave the service stopped until the replacement jar is installed; do not run the backup section's final `systemctl start` command yet.
3. Preserve the current jar, install the new jar beside it, then replace the live file:

   ~~~bash
   sudo cp --preserve=mode,ownership /var/lib/jahaziel/jahaziel.jar /var/lib/jahaziel/jahaziel.jar.previous
   sudo install --owner=jahaziel --group=jahaziel --mode=0640 ./jahaziel.jar /var/lib/jahaziel/jahaziel.jar.next
   sudo mv /var/lib/jahaziel/jahaziel.jar.next /var/lib/jahaziel/jahaziel.jar
   ~~~

4. Start the service and repeat the health and browser checks.
5. After confirming the upgrade, retain `jahaziel.jar.previous` and the backup archive according to your backup policy.

Database migrations can move data forward. If an upgrade must be rolled back, stop the service, restore the pre-upgrade `.env` and `data/` archive, copy `jahaziel.jar.previous` back to `jahaziel.jar`, then start the old version. Do not start an old jar against data a newer version may have migrated. The CLI `upgrade` command skips replacement when both jars have the same version; build artifacts can share a snapshot version, so the explicit replacement above works for either case.

## Data layout

The service account owns the jar directory and application data:

| Path | Contents |
|---|---|
| `/var/lib/jahaziel/jahaziel.jar` | Executable application |
| `/var/lib/jahaziel/.env` | Identity-provider and runtime configuration |
| `/var/lib/jahaziel/data/jahaziel.db` | SQLite database |
| `/var/lib/jahaziel/data/workspaces/` | Separate workspace homes |
| `/var/lib/jahaziel/data/language-packs/` | Explicitly selected shared language tools |
| `/var/lib/jahaziel/data/keys/` | Application signing keys |
| `/var/lib/jahaziel/data/logs/` | Application logs |

Back up `.env` and the full `data/` directory. Protect both from unauthorized access and store copies off host. The CLI `backup` command writes its archive beside the jar.

## Troubleshooting

| Symptom | Check |
|---|---|
| `doctor` reports unsupported architecture | Use a native Linux x64 or arm64 host and matching artifact. |
| `doctor` reports an old Java version | Use a Java 25+ runtime and update the path in the service unit. |
| `doctor` cannot find `setsid` | Install the distribution's `util-linux` package. |
| Login is disabled or rejected | Check all five `BASIS_*` values and verify the callback URL, issuer, client, and resource with the identity-provider administrator. |
| Browser sign-in returns to the wrong URL | Ensure `BASIS_REDIRECT_URI` exactly matches the public URL and `JAHAZIEL_DOMAIN` contains only the base hostname. |
| HTTPS works but preview links fail | Confirm exact DNS records for both `code.example.com` and `preview.code.example.com`, automatic certificates for both, the preview site in Caddy, and the `workspaceId/port` preview path. |
| The embedded preview is blank while a new tab works | Confirm the IDE `frame-src` permits the configured preview host and the preview host's `frame-ancestors` permits the IDE host. |
| The service cannot write data | Confirm the `jahaziel` user owns `/var/lib/jahaziel` and all contents. |
| Port `11451` is occupied | Stop the other listener or choose a different `PORT`, then update the unit and Caddy upstream together. |
| A task cannot launch a project | Install that project's runtime on the host; Jahaziel does not bundle Node.js, Python, or compilers. |
| `doctor` detects another running copy | Stop the other service before using the same data directory. |

For safe resets, verification, and release checks, see [operations](../operations/README.md).
