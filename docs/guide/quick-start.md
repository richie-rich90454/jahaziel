<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Quick start

Last verified against version 0.1.0-SNAPSHOT.

The fastest local run is a packaged jar; production should also register a
supervisor. The full step-by-step procedures are in
[Deploy Jahaziel on a single server](../deployment/single-server.md).

## Run a packaged jar locally

1. Use an existing Java 25 runtime on a Linux, macOS, or Windows host (Linux x64 and arm64 are the CI-verified targets).
2. Create a folder, copy `jahaziel.jar` into it.
3. Run `java -jar jahaziel.jar init` — writes `.env` and `data/`.
4. Fill the canonical basis-auth keys in `.env`, or configure another provider — see [authentication providers](./configuration.md#authentication-providers). To see the UI first, use `--demo` instead; it is for local tests only.
5. Run `java -jar jahaziel.jar doctor` and review host, writable paths, backend, and disk usage. `capabilities` reports which optional tools are available.
6. Run `java -jar jahaziel.jar` — open `http://localhost:11451`. Health is at `http://localhost:11451/actuator/health`.

On Windows the same commands work in PowerShell; use `java -jar jahaziel.jar`
from the jar directory. Override the port with `--port=N` or `PORT` in `.env`,
and the data root with `--data-dir=PATH` (default `data/` beside the jar).
Real environment variables override `.env`, and configuration is read at
startup, so a credential change requires a restart.

## Canonical basis-auth keys

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
~~~

Register `${CURRENT_URL_ORIGIN}/api/auth/basis/callback` as the client callback,
and `${CURRENT_URL_ORIGIN}/api/auth/basis/backchannel` for backchannel events.
`CURRENT_URL_ORIGIN` is the public HTTPS origin with no trailing path and
derives both URLs. `BASIS_AUTH_RESOURCE_SECRET` verifies webhook proofs and
authenticates internal API calls; `BASIS_AUTH_INTERNAL_URL` is the private
listener for webhook verification and user-state checks.
`BASIS_AUTH_COOKIE_SECRET` is at least 32 random bytes; sessions are stored
server-side and tokens signed with generated keys in `data/keys`. Legacy
`BASIS_REDIRECT_URI`, `BASIS_CLIENT_ID`, `BASIS_CLIENT_SECRET`, and
`BASIS_RESOURCE` aliases remain accepted. This app stores state in a
jar-adjacent SQLite database (`data/jahaziel.db`); it does not require an
external database service.

## The other deployment paths

- **Supervised install:** register the jar with systemd, launchd, or WinSW so it restarts on failure and captures logs. See [service templates per OS](../deployment/single-server.md#service-templates-per-os).
- **Repo clone on the target:** for air-gapped hosts, source audit, or local patches. Prerequisites (JDK 25, Node 24, pnpm 12.14.0) must already exist; build with `pnpm --dir web install --frozen-lockfile && pnpm --dir web build` and `./mvnw -B package -DskipTests -Dmaven.test.skip=true`, with caches kept repo-local. See [Path 3](../deployment/single-server.md#path-3--clone-the-repository-on-the-target).

## Updates and rollback

Before replacing a jar, run `java -jar jahaziel.jar backup` and
`java -jar jahaziel.jar support-bundle`. Then stage the new jar with
`java -jar jahaziel.jar update --to <new-jar> --sums SHA256SUMS --dry-run`
followed by the same command without `--dry-run`. The swap happens on the next
start; stop the service first, especially on Windows. If the new jar fails
checks, stop it again and run `java -jar jahaziel.jar update --rollback`. See
[Updating and rollback](../deployment/single-server.md#updating-and-rollback).

See [using Jahaziel](./using-jahaziel.md) for the workbench buttons and states,
[configuration](./configuration.md) for every variable, and
[demo mode](./demo-mode.md) for the zero-config path.

Windows and macOS computers are browser clients and can also host the jar.
Native macOS and Windows host acceptance is still open.
