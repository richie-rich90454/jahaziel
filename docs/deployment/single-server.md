<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Deploy Jahaziel on a single server

This guide covers the three supported ways to put one Jahaziel jar on a host:
[upload a packaged jar](#path-1--packaged-jar-upload),
[run that jar under the host's supervisor](#path-2--run-under-an-os-supervisor),
and [clone the repository on the target](#path-3--clone-the-repository-on-the-target).
It then explains how to [update and roll back](#updating-and-rollback) an
existing install. The reference production target is Ubuntu 26.04 LTS on Linux
x64 or arm64 with Caddy for HTTPS; the same jar and jar-adjacent layout run on
macOS and Windows with the [service templates per OS](#service-templates-per-os),
but those hosts have not passed real-host acceptance yet.

Jahaziel is a remote source IDE for trusted users. Workspaces have separate
homes, but terminals, tasks, Git, and debug adapters run as native processes
under the same service account. This is not per-user OS isolation; do not expose
the deployment to mutually untrusted users.

## Deployment shape

| Component | Value |
|---|---|
| Server | Linux x86_64 or arm64 (CI-verified); macOS and Windows also run the same jar |
| Java | Runtime 25 or newer |
| Application | One executable jar, `jahaziel.jar` |
| Application port | `11451`, bound to loopback by the supplied service definitions |
| Public access | Caddy on ports 80 and 443, with separate IDE and preview hostnames |
| Persistent app files | `<jar>/.env` and `<jar>/data/` (SQLite at `data/jahaziel.db`) |
| Login | One or more authentication provider plugins; basis-auth is the reference OIDC provider |

Use a DNS name you control, such as `code.example.com`. The preview host is
`preview.code.example.com`. Replace both examples with your real names
throughout this guide. This app stores state in a jar-adjacent SQLite database
(`data/jahaziel.db`); it does not require an external database service.

## Service templates per OS

All three paths end in the same layout: copy `jahaziel.jar` into a writable
directory, run `java -jar jahaziel.jar init` and `doctor` there, then register
the host's supervisor. Application state is **jar-adjacent**: `.env`, `data/`
(SQLite, workspace homes, keys, logs, language packs, update state) and the
configured temp directory stay under `<jar>/`. Only the service definition and
the Caddy config live outside that directory, where the OS requires them.

| OS | Service template | Service definition | Install helper | Caddy template |
|---|---|---|---|---|
| Linux | `ops/services/jahaziel.service` | `/etc/systemd/system/jahaziel.service` | `systemctl daemon-reload`, then `systemctl enable --now jahaziel` | `ops/caddy/Caddyfile.linux` |
| macOS | `ops/launchd/dev.jahaziel.plist.template` | `/Library/LaunchDaemons/<label>.plist` | `ops/launchd/install.sh <jar-dir> [label]` | `ops/caddy/Caddyfile.macos` |
| Windows | `ops/windows/jahaziel-service.xml.template` | WinSW service registered with Windows Services; its `jahaziel-service.xml` config stays jar-adjacent | `ops/windows/install.ps1 -JarDir <jar-dir> -WinSw <winsw.exe>` | `ops/caddy/Caddyfile.windows` |

The launchd and WinSW templates substitute the jar directory into the
definition and keep stdout/stderr logs and `java.io.tmpdir` under `<jar>/data`.
The systemd unit uses `PrivateTmp` and the journal instead. Each `Caddyfile.<os>`
template reverse-proxies the loopback jar port with HSTS and security headers;
copy the matching file to that host's Caddy config location and replace the
example hostnames. The default `ops/caddy/Caddyfile` is the Linux-compatible
baseline.

## Path 1 — Packaged jar upload

Use this for a normal install: build on a dev machine (or download a CI
artifact), copy one file to the host, initialize it, and supervise it.

### 1. Build on a dev machine

Linux/macOS (bash):

~~~bash
./mvnw -B verify -Psmoke
# or, to skip tests entirely:
./mvnw -B package -DskipTests -Dmaven.test.skip=true
# packaged jar: target/jahaziel.jar
~~~

Windows (native PowerShell — no WSL, no global tool installs):

~~~powershell
.\mvnw.cmd -B verify -Psmoke
# or:
.\mvnw.cmd -B package -DskipTests -Dmaven.test.skip=true
# packaged jar: target\jahaziel.jar
~~~

`verify -Psmoke` includes the packaged browser smoke checks and is the closer
match to CI; the `package` form is faster when you only need the jar. Both use
the Maven wrapper. Alternatively, download the `jahaziel-linux-x64` or
`jahaziel-linux-arm64` artifact from a successful
[Linux build workflow](https://github.com/richie-rich90454/jahaziel/actions/workflows/linux.yml)
run and extract `jahaziel.jar`. The workflow output is a build artifact for
evaluation, not a stable release package; pin the commit you intend to deploy.

### 2. Copy the jar to the host

Linux (dedicated service account and a writable jar directory):

~~~bash
sudo useradd --system --create-home --home-dir /var/lib/jahaziel --shell /usr/sbin/nologin jahaziel
sudo install --directory --owner=jahaziel --group=jahaziel --mode=0750 /var/lib/jahaziel
scp target/jahaziel.jar jahaziel@example-host:/var/lib/jahaziel/jahaziel.jar
# or: rsync -av target/jahaziel.jar jahaziel@example-host:/var/lib/jahaziel/
sudo install --owner=jahaziel --group=jahaziel --mode=0640 /var/lib/jahaziel/jahaziel.jar /var/lib/jahaziel/jahaziel.jar
~~~

Skip `useradd` if the account exists; keep its home data and confirm it owns
`/var/lib/jahaziel`. macOS uses the same commands with a directory such as
`/usr/local/jahaziel`. Windows (PowerShell):

~~~powershell
New-Item -ItemType Directory -Force -Path C:\jahaziel | Out-Null
scp .\target\jahaziel.jar jahaziel@example-host:C:\jahaziel\jahaziel.jar
# or from a file share:
Copy-Item .\target\jahaziel.jar \\example-host\share\jahaziel\jahaziel.jar
~~~

### 3. Initialize and configure `.env`

`init` writes `.env` beside the jar and creates `data/`; it refuses to overwrite
an existing `.env`. Run it as the service account:

~~~bash
# Linux / macOS
sudo -u jahaziel -- sh -c 'cd /var/lib/jahaziel && java -jar jahaziel.jar init'
sudoedit /var/lib/jahaziel/.env
sudo chown jahaziel:jahaziel /var/lib/jahaziel/.env
sudo chmod 0600 /var/lib/jahaziel/.env
~~~

~~~powershell
# Windows
cd C:\jahaziel
java -jar jahaziel.jar init
notepad .\.env
~~~

The canonical, lean basis-auth setup is:

~~~dotenv
BASIS_AUTH_ISSUER=https://idp.example.com
BASIS_AUTH_CLIENT_ID=your-client-id
BASIS_AUTH_CLIENT_SECRET=your-client-secret
BASIS_AUTH_RESOURCE=urn:basis:api:jahaziel
BASIS_AUTH_RESOURCE_SECRET=your-resource-secret
BASIS_AUTH_INTERNAL_URL=http://127.0.0.1:3001
BASIS_AUTH_COOKIE_SECRET=<at-least-32-random-bytes>
CURRENT_URL_ORIGIN=https://code.example.com
PORT=11451
JAHAZIEL_LOG_LEVEL=INFO
JAHAZIEL_BACKEND=local
# Optional: persist share-link signing across restarts.
# JAHAZIEL_SHARE_SECRET=<long-random-secret>
~~~

Register these exact URLs in basis-auth:
`https://code.example.com/api/auth/basis/callback` (redirect URI) and
`https://code.example.com/api/auth/basis/backchannel` (backchannel receiver).
`CURRENT_URL_ORIGIN` is the public HTTPS origin with no trailing path and
derives both URLs. `BASIS_AUTH_RESOURCE_SECRET` verifies backchannel webhook
proofs and authenticates internal API calls; `BASIS_AUTH_INTERNAL_URL` is the
private listener for webhook verification and fail-closed user-state checks.
`BASIS_AUTH_COOKIE_SECRET` is at least 32 random bytes; sessions are stored
server-side and tokens signed with generated keys in `data/keys`, so it is
accepted but not used to encrypt cookies. Login stays off until the issuer,
client, resource, and origin are set. Keep `.env` private. Configuration is
read at startup, so a credential change requires a restart.

Legacy `BASIS_REDIRECT_URI`, `BASIS_CLIENT_ID`, `BASIS_CLIENT_SECRET`, and
`BASIS_RESOURCE` remain accepted as backward-compatible aliases; use one
spelling consistently.
Optional Microsoft Entra keys in the canonical set are `MICROSOFT_TENANT_ID`,
`MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, and `MICROSOFT_WEBHOOK_STATE`
/ `MICROSOFT_WEBHOOK_ID`; this build reads `MICROSOFT_ISSUER` (tenant in the
URL), `MICROSOFT_CLIENT_ID`, and `MICROSOFT_CLIENT_SECRET`. Grant administrator
rights through the identity provider or database — there is no `.env`
admin-bootstrap key in this build. See the
[basis-auth integration guide](../basis_auth_setup_guide.md) for the full
downstream contract and secret rotation.

For a direct run without Caddy (private network or evaluation only), start the
jar after `doctor`:

~~~bash
java -jar jahaziel.jar
# or: java -jar jahaziel.jar --port=11451 --data-dir=/var/lib/jahaziel/data
~~~

`--port=N` overrides `PORT`, and `--data-dir=PATH` overrides the default `data/`
beside the jar. Without Caddy, previews still need host routing for the
configured preview hostname at `/{workspaceId}/{port}/`.

### 4. Preflight, health, and capabilities

~~~bash
# Linux / macOS
sudo -u jahaziel -- sh -c 'cd /var/lib/jahaziel && java -jar jahaziel.jar doctor'
sudo -u jahaziel -- sh -c 'cd /var/lib/jahaziel && java -jar jahaziel.jar capabilities'
curl --fail --show-error http://127.0.0.1:11451/actuator/health
~~~

~~~powershell
# Windows
cd C:\jahaziel
java -jar jahaziel.jar doctor
java -jar jahaziel.jar capabilities
Invoke-WebRequest http://127.0.0.1:11451/actuator/health
~~~

`doctor` checks Java, host architecture, writable paths, `.env`, the port,
SQLite storage, process state, and Git availability; fix every failed check.
`capabilities` reports which optional tools are available and marks them
unavailable under `--offline`. Health is served at `/actuator/health` on the
application port (and through the Caddy hostname once configured).

**Demo mode is for local tests only.** `java -jar jahaziel.jar --demo` stubs the
identity provider, seeds a sample user/workspace, and binds to loopback. Never
expose it to a network or use it with real data.

## Path 2 — Run under an OS supervisor

Register the jar with the host's supervisor for restart-on-failure,
boot-at-start, and log capture. Complete path 1 first; the jar directory,
`.env`, and `data/` are identical on every OS.

### Linux (systemd)

The included unit runs as `jahaziel`, runs `doctor` before each start, binds the
app to `127.0.0.1`, and allows writes only under `/var/lib/jahaziel`:

~~~bash
sudo install -o root -g root -m 0644 ops/services/jahaziel.service /etc/systemd/system/jahaziel.service
sudo systemctl daemon-reload
sudo systemctl enable --now jahaziel
sudo systemctl status jahaziel --no-pager        # status
sudo journalctl -u jahaziel -f                   # logs
sudo systemctl restart jahaziel                  # restart
sudo systemctl stop jahaziel                     # stop
~~~

If you copied only the jar artifact, fetch the unit from the same commit as the
jar. If Java is not at `/usr/bin/java`, update `ExecStartPre`/`ExecStart` in the
unit to the path returned by `command -v java`, then reload and restart.

### macOS (launchd)

`install.sh` creates `data/logs` and `data/tmp`, substitutes `__JAR_DIR__` into
the plist, writes it to `/Library/LaunchDaemons`, and loads it:

~~~bash
sudo sh ops/launchd/install.sh /var/lib/jahaziel dev.jahaziel  # install + bootstrap
launchctl print system/dev.jahaziel                            # status
tail -f /var/lib/jahaziel/data/logs/launchd.out.log /var/lib/jahaziel/data/logs/launchd.err.log
launchctl kickstart -k system/dev.jahaziel                     # restart
launchctl bootout system/dev.jahaziel                          # stop
sudo sh ops/launchd/uninstall.sh dev.jahaziel                  # remove
~~~

The plist uses `RunAtLoad` and `KeepAlive` for boot-at-start and
restart-on-failure, sets `java.io.tmpdir` under `<jar>/data/tmp`, and captures
stdout/stderr under `<jar>/data/logs`. The service definition lives in
`/Library/LaunchDaemons`, outside the jar directory, as macOS requires.

### Windows (WinSW)

Supply a WinSW executable; `install.ps1` writes `jahaziel-service.xml`
jar-adjacent, registers the service, and starts it with `Automatic` start mode:

~~~powershell
.\ops\windows\install.ps1 -JarDir C:\jahaziel -WinSw C:\tools\WinSW.exe
Get-Service -Name jahaziel                       # status
Get-Content C:\jahaziel\data\logs\*.log -Wait    # logs
Restart-Service -Name jahaziel                   # restart
Stop-Service -Name jahaziel                      # stop
Start-Service -Name jahaziel                     # start
.\ops\windows\uninstall.ps1 -JarDir C:\jahaziel -WinSw C:\tools\WinSW.exe
~~~

The XML template binds the jar to `127.0.0.1`, puts `java.io.tmpdir` and rolled
logs under `<jar>\data`, and restarts the service five seconds after a failure.
Stop the service before any jar update: Windows keeps a running jar file
locked, so an in-place replacement fails until the process exits.

### Caddy and HTTPS

Install Caddy with its
[official instructions](https://caddyserver.com/docs/install). Add both exact
hosts to the host's Caddyfile; replace the example names in the included
[`ops/caddy/Caddyfile`](../../ops/caddy/Caddyfile) and its content security
policy. Use `ops/caddy/Caddyfile.linux`, `Caddyfile.macos`, or
`Caddyfile.windows` for the matching OS:

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

Make sure both hostnames resolve to this server, then validate and reload:

~~~bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
~~~

Caddy obtains and renews ordinary certificates for both exact hostnames via
built-in ACME; no wildcard DNS or certificate is needed. Its reverse proxy
forwards terminal, language-service, debugger, preview, and hot-reload
WebSockets. Do not publish port `11451` directly. Keep `JAHAZIEL_DOMAIN` set to
the base host (`code.example.com`), without `https://`; see the
[port and preview reference](../reference/ports.md).

### Verify the deployment

~~~bash
curl --fail --show-error http://127.0.0.1:11451/actuator/health
curl --fail --show-error https://code.example.com/actuator/health
~~~

Then complete a browser smoke check:

1. Open `https://code.example.com` and sign in through the configured provider.
2. Create an empty workspace and confirm it becomes ready.
3. Create and save a text file, then reopen it.
4. Open a terminal and confirm its working directory belongs to that workspace.
5. Run a task only if its project runtime is installed on the host.
6. Start a preview task, confirm it is reachable at
   `https://preview.code.example.com/{workspaceId}/{port}/`, and check both
   embedded and new-tab preview opening.
7. Restart Jahaziel and verify the workspace and saved file remain.

Node.js and Python starter tasks need their matching host runtimes. The Python
and static-site templates need `python3`; the Node template needs Node.js
22.18.0 or newer. The jar does not contain them. See
[templates](../guide/templates.md) and
[language diagnostics](../guide/using-jahaziel.md#language-diagnostics).

## Path 3 — Clone the repository on the target

Prefer this path when the target is air-gapped, when you must audit or patch
the source, or when you cannot move a binary from a build machine. The build
runs on the target and produces the same `jahaziel.jar` as paths 1 and 2.

Prerequisites must already exist on the machine — the build installs nothing
globally: JDK 25, Node.js 24 or newer, and pnpm 12.14.0 (pinned by
`web/package.json`). Use the wrapper `./mvnw` / `mvnw.cmd`; do not install
tools system-wide just to build.

Linux/macOS (bash):

~~~bash
git clone https://github.com/richie-rich90454/jahaziel.git
cd jahaziel
git checkout <release-tag-or-commit>
pnpm --dir web install --frozen-lockfile && pnpm --dir web build
./mvnw -B package -DskipTests -Dmaven.test.skip=true
# packaged jar: target/jahaziel.jar
sudo install --owner=jahaziel --group=jahaziel --mode=0640 target/jahaziel.jar /var/lib/jahaziel/jahaziel.jar
~~~

Windows (native PowerShell):

~~~powershell
git clone https://github.com/richie-rich90454/jahaziel.git
cd jahaziel
git checkout <release-tag-or-commit>
pnpm --dir web install --frozen-lockfile
pnpm --dir web build
.\mvnw.cmd -B package -DskipTests -Dmaven.test.skip=true
Copy-Item .\target\jahaziel.jar C:\jahaziel\jahaziel.jar
~~~

Pin a release tag or commit rather than tracking a branch. The project version
is `0.1.0-SNAPSHOT` and workflow artifacts are evaluation builds, so the commit
is the reliable identity of what you deployed.

Caches stay **repo-local**, not in a user or system store. On Windows the
repository's `dev.ps1` verbs (`bootstrap`, `build`) set `LOCALAPPDATA`,
`PNPM_HOME`, `PNPM_CONFIG_STORE_DIR`, `PNPM_CONFIG_CACHE_DIR`, and
`PNPM_CONFIG_STATE_DIR` under the ignored `.cache/` directory; with the raw
commands above, set those variables (and
`MAVEN_OPTS=-Dmaven.repo.local=<repo>/.cache/maven-repo`) yourself. Use
`.\dev.ps1 cache:report` to inspect sizes and `.\dev.ps1 cache:prune --apply`
to remove generated caches.

Finish by running `init`, editing `.env`, `doctor`, and registering the
supervisor exactly as in paths 1 and 2. Building on the target avoids trusting
a binary from elsewhere, but you still need a protected copy of `.env` and
`data/` for recovery.

## Back up and restore

Stop Jahaziel before making a consistent backup:

~~~bash
sudo systemctl stop jahaziel
sudo -u jahaziel -- sh -c 'umask 077; exec java -jar /var/lib/jahaziel/jahaziel.jar backup'
~~~

On Windows, `Stop-Service -Name jahaziel` first, then
`java -jar C:\jahaziel\jahaziel.jar backup` from the jar directory. The
`backup` command uses `tar` on PATH and writes
`jahaziel-backup-<stamp>.tar.gz` beside the jar, containing `.env` and `data/`.
It prints the path and SHA-256 digest. `--no-secrets` omits `.env` and
`data/keys/jwt.key`. Treat the archive as secret material and copy it to
protected off-host storage:

~~~bash
scp /var/lib/jahaziel/jahaziel-backup-TIMESTAMP.tar.gz backupuser@backup.example.com:/srv/backups/jahaziel/
~~~

`support-bundle` writes `jahaziel-support-<stamp>.zip` with redacted `.env`,
`doctor` output, version/Java/host details, and small logs. It is the safe
artifact to attach to a support request; never attach raw `.env`, keys, or
workspace source. Restart the service after backing up:

~~~bash
sudo systemctl start jahaziel
~~~

To restore an archive, stop the service and preserve the current state in a
uniquely named directory so the restored `data/` tree starts clean:

~~~bash
set -euo pipefail
sudo systemctl stop jahaziel
restore_hold="/var/lib/jahaziel/pre-restore-$(date +%Y%m%d-%H%M%S)"
sudo install --directory --owner=jahaziel --group=jahaziel --mode=0700 "$restore_hold"
sudo mv /var/lib/jahaziel/.env "$restore_hold/.env"
sudo mv /var/lib/jahaziel/data "$restore_hold/data"
sudo install --owner=jahaziel --group=jahaziel --mode=0600 /path/to/jahaziel-backup.tar.gz /var/lib/jahaziel/restore.tar.gz
sudo -u jahaziel -- java -jar /var/lib/jahaziel/jahaziel.jar restore /var/lib/jahaziel/restore.tar.gz
sudo systemctl start jahaziel
~~~

The script stops at the first failure, so a failed restore does not remove the
archive or start against partial data. Verify sign-in, workspaces, and saved
files before removing the temporary archive. Keep the `pre-restore-*`
directory until you no longer need the previous state for rollback. See the
[backup operations guide](../operations/backup.md).

## Updating and rollback

### Before you update: backup and support bundle

Read the [CHANGELOG](../../CHANGELOG.md) for the target version, then capture
both a restorable state and a diagnostics snapshot **before** touching the
jar:

~~~bash
# Linux / macOS, service stopped for a consistent backup
sudo systemctl stop jahaziel
sudo -u jahaziel -- sh -c 'umask 077; exec java -jar /var/lib/jahaziel/jahaziel.jar backup'
sudo -u jahaziel -- sh -c 'cd /var/lib/jahaziel && java -jar jahaziel.jar support-bundle'
~~~

On Windows, `Stop-Service -Name jahaziel` first and run both commands from
`C:\jahaziel`. Copy the backup off host and record its printed SHA-256. Keep
the service stopped for the update: a running jar cannot be swapped safely,
and Windows holds a lock on it.

### How the staged updater works

`java -jar jahaziel.jar update --to <jar>` stages a candidate jar at
`<data>/update/<version>/jahaziel.jar`, verifies it against `SHA256SUMS`, and
writes a `pending` marker. Nothing touches the running jar during staging. On
the **next start**, the application re-verifies the staged jar, copies the
current live jar to `<data>/update/rollback/<old-version>/jahaziel.jar`, swaps
the staged jar into place, writes an `applied` marker naming the old version,
and removes `pending`. A staged jar that fails verification is discarded
without touching the running jar.

Commands (from `Cli.java`):

- `update --to <jar> [--sums <file>] [--version <v>] [--dry-run]` — stage and
  verify. With no `--sums`, the updater looks for `SHA256SUMS` beside the
  candidate; if absent it generates one from the candidate, which checks copy
  integrity but **not** publisher authenticity. Supply the published
  `SHA256SUMS` for a real release.
- `update --status` — show the update root, pending version, applied rollback
  target, and staged versions.
- `update --rollback` — restore the jar saved before the last applied update.

The older `upgrade --to <jar>` replaces the jar immediately and keeps a
sibling `jahaziel.jar.bak-<stamp>`; it has no staged checksum flow and is for
offline replacements when the service is already stopped.

### Step-by-step update

1. Stop the service (`sudo systemctl stop jahaziel` or
   `Stop-Service -Name jahaziel`). Do this first on every OS and especially on
   Windows.
2. Back up `.env` and `data/`, and write a support bundle, as above.
3. Read the [CHANGELOG](../../CHANGELOG.md) for behavior and migration notes.
4. Download the new `jahaziel.jar` and its published `SHA256SUMS` into a
   staging directory outside the live jar directory.
5. Dry-run the staging:

   ~~~bash
   java -jar jahaziel.jar update --to ./jahaziel-NEW.jar --sums ./SHA256SUMS --version 1.2.3 --dry-run
   ~~~

6. Stage for real (same flags without `--dry-run`):

   ~~~bash
   java -jar jahaziel.jar update --to ./jahaziel-NEW.jar --sums ./SHA256SUMS --version 1.2.3
   java -jar jahaziel.jar update --status
   ~~~

7. Start the service so the swap runs
   (`sudo systemctl start jahaziel` / `Start-Service -Name jahaziel`).
   Confirm the applied line in the log or journal.
8. Run `update --status`, `doctor`, and the health check
   (`curl --fail http://127.0.0.1:11451/actuator/health`).
9. In the browser, verify you can sign in, that a workspace and a saved file
   are present, and that a preview opens.

### Rollback

If the new jar fails health checks or workspace verification:

1. Stop the service. Stopping first is mandatory on Windows and prevents an
   old/new jar mix from serving requests.
2. Run `java -jar jahaziel.jar update --status` to confirm the applied rollback
   target, then `java -jar jahaziel.jar update --rollback`. This copies
   `<data>/update/rollback/<old-version>/jahaziel.jar` back over the live jar
   and clears the `applied` marker.
3. Start the service and re-run `doctor`, the health check, sign-in, and the
   workspace/task/preview smoke checks.

Manual fallback, if the marker or rollback copy is missing: restore the saved
jar from `<data>/update/rollback/<old-version>/jahaziel.jar` or a
`jahaziel.jar.bak-<stamp>` sibling over `jahaziel.jar`, then start the service.
If the new version migrated the SQLite database, also restore the pre-update
`.env` and `data/` from the backup you took in step 2 — **do not start an old
jar against data a newer version may have migrated**. A rollback restores the
previous jar and, when needed, the previous state; it is not just a file copy
back. Record the incident and the reason in your change log.

## Data layout

The update flow only replaces `jahaziel.jar`. All application state stays in
the jar-adjacent data directory:

| Path | Contents |
|---|---|
| `<jar>/.env` | Canonical `BASIS_AUTH_*` keys, legacy aliases, and runtime settings (read at startup) |
| `data/jahaziel.db` | SQLite database: workspaces, users, tasks, and app state |
| `data/workspaces/` | Separate workspace homes and source files |
| `data/keys/` | Application signing keys |
| `data/identity/` | Cached identity and provider state |
| `data/logs/` | Application and supervisor logs |
| `data/language-packs/` | Explicitly selected shared language tools |
| `data/update/<version>/` | Staged candidate jars and their `SHA256SUMS` |
| `data/update/rollback/<version>/` | Previous live jar saved by the last applied update |
| `data/update/{pending,applied}` | Markers for the staged and last-applied versions |
| `data/tmp/` | Configured temp directory (used by the launchd/WinSW templates) |

Keep `data/update/` intact: deleting the rollback copy removes the ability to
roll back with `update --rollback`. Back up `.env` and `data/` together; the
service definition is the only piece kept outside the jar directory.

## Troubleshooting

| Symptom | Check |
|---|---|
| `doctor` reports unsupported architecture | Use a native Linux x64 or arm64 host and matching artifact. |
| `doctor` reports an old Java version | Use a Java 25+ runtime and update the path in the service unit. |
| `doctor` cannot find `setsid` | Install the distribution's `util-linux` package. |
| Login is disabled or rejected | Check the canonical basis-auth keys, match the callback and resource with the provider, and restart after any `.env` change. |
| Browser sign-in returns to the wrong URL | Ensure `CURRENT_URL_ORIGIN` is the exact public HTTPS origin with no trailing path, and that the redirect URI registered in basis-auth is `${CURRENT_URL_ORIGIN}/api/auth/basis/callback`. |
| HTTPS works but preview links fail | Confirm exact DNS for both `code.example.com` and `preview.code.example.com`, certificates for both, the preview site in Caddy, and the `workspaceId/port` path. |
| The embedded preview is blank while a new tab works | Confirm the IDE `frame-src` permits the preview host and the preview host's `frame-ancestors` permits the IDE host. |
| The service cannot write data | Confirm the service account owns the jar directory and all contents. |
| Port `11451` is occupied | Stop the other listener or choose a different `PORT`, then update the unit and Caddy upstream together. |
| A task cannot launch a project | Install that project's runtime on the host; the jar does not bundle Node.js, Python, or compilers. |
| `doctor` detects another running copy | Stop the other service before using the same data directory. |
| `update` reports a checksum failure | Re-download the jar and the matching published `SHA256SUMS`; remove the bad staged copy under `data/update/<version>/` and retry. |
| Jar replacement fails on Windows | Stop the WinSW service first; a running process locks `jahaziel.jar`. |
| New jar fails health checks | Stop the service and run `update --rollback`; restore the pre-update backup if data was migrated. |

For safe resets, verification, and release checks, see
[operations](../operations/README.md) and the
[CLI reference](../reference/cli.md).
