<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Jahaziel: the definitive plan

Source of truth for the release. Host: Windows, macOS, or Linux — x64 or
arm64. No admin. No WSL, no Docker, no system services. Runs from any
user-writable folder, e.g. `C:\Users\<user>\Desktop\jahaziel\`,
`~/Desktop/jahaziel/`, `/opt/jahaziel/`. Port `11451`. No CI. Same behavior
on every OS/arch combo — only the REH binary and the terminal shell differ.
Docs are part of every phase's done-when. Phase 9 is human-executed.

## Contracts

1. **Run:** folder with `jahaziel.jar` + `.env` → `java -jar jahaziel.jar`
   starts the full stack on `http://localhost:11451` and serves the UI. All
   state in `./data/`. Nothing written outside that folder. SIGINT/SIGTERM
   shuts down cleanly within 5 seconds.
2. **Cross-OS:** Windows/macOS/Linux x64/arm64 run the same jar, command,
   and layout. Only the REH binary and terminal shell differ.
3. **Zero-privilege:** JDK 25 only, no admin. No WSL, Docker, installer.
4. **No host supervisor:** one JVM, one process. `ServiceUnit` /
   `ServiceSupervisor` live only inside the guest image, never on the host
   jar's startup path.
5. **Data locality:** `./data/` is the entire install state. Delete it for
   factory-fresh; back it up for a full backup.
6. **Docs:** code without matching doc updates is incomplete. Stale docs are
   bugs; delete rather than keep stale.
7. **Acceptance:** Phase 9 is human-executed. The agent prepares the repo,
   then stops and asks.

## Foundation (make it work)

### Phase 1 — One pom, one jar

`./mvnw -q -DskipTests package` → `target/jahaziel.jar`. One root `pom.xml`,
no `<modules>`. Merge `control-plane/`, `node-agent/`, `gateway/`, `spi/`,
`dist/` into one `src/main/java/` with packages preserved. Union deps,
dedupe on `groupId:artifactId`. One shade/repackage, not both.
`<finalName>jahaziel</finalName>`. One ArchUnit test:
`slices().matching("dev.jahaziel.(*)..").should().beFreeOfCycles()`.
Docs: `README.md` Build + Layout; `docs/architecture/overview.md` package
refs. Done when the jar builds on all six combos.

### Phase 2 — Platform abstraction

Every OS/arch-conditional funnels through `dev.jahaziel.platform.Platform`
(`WindowsPlatform`, `PosixPlatform`): `os()`, `arch()`, `jarDir()`,
`dataDir()`, `tempDir()`, `rehDir()`, `rehVariant()` (win32/darwin/linux ×
x64/arm64), `rehExecutable()`, `rehLaunchArgs()`, `terminal()`,
`pathSeparator()`, `toNative()`/`toPosix()`, `subprocessEnv()`
(`TEMP`/`TMP`/`TMPDIR` → `data/tmp`). Subprocess paths → `toNative()`;
POSIX script paths → `toPosix()`. No `os.name`/`os.arch` outside the package.
Unsupported → exit 1 naming detected + supported combos. Doc:
`docs/architecture/platform.md`. Done when the grep outside `platform`
returns nothing.

### Phase 3 — Run model

`JahazielMain` before Spring: resolve `jarDir` via `CodeSource` (not
`user.dir`); set `java.io.tmpdir` → `jarDir/data/tmp/`; parse
`jarDir/.env` (CRLF/LF/`#`/quotes); real env wins; validate five `BASIS_*`
(missing → names + path, exit 1); set `server.port=11451`,
`jahaziel.data-dir=<jarDir>/data`; `SpringApplication.run(...)`. First run
creates `data/`, `logs/`, `tmp/`, `workspaces/`, `ide/`, `backups/`,
`dumps/`, `plugins/`, `templates/`; `.initialized` after Flyway; unwritable
→ exit 1 with path; `--data-dir` overrides, no `$HOME` fallback. SQLite:
`WAL`, `busy_timeout=5000`, `foreign_keys=ON`, `toPosix()` JDBC URL.
Graceful shutdown 20s. Temp via `platform.tempDir()` + `subprocessEnv()`.
Docs: README Quick Start + writes table, `docs/deployment/single-server.md`,
`docs/operations/backup.md`, `docs/operations/reset.md`,
`docs/reference/cli.md`. Done when no-`.env` prints help, with-`.env`
serves `/actuator/health` on 11451.

### Phase 4 — REH bundling

Six variants: `vscode-reh-web-{win32,darwin,linux}-{x64,arm64}-min`. Three
build modes, same run contract: `fat` (default, all six, 1.5–2.5 GB),
`slim` (`-Djahaziel.reh=&lt;variant&gt;`, ~300–500 MB), `download`
(`-Djahaziel.reh=download`, manifest + checksums, fetch to
`data/ide/&lt;variant&gt;/` on first run). Manual dance: `ide/build.sh --target
&lt;variant&gt;` per combo → `ide/out/` → `./mvnw package
-Djahaziel.bundle.ide=ide/out/`. Runtime: `rehVariant()` selects, extract
once to `data/ide/&lt;variant&gt;/` guarded by
`.extracted-&lt;version&gt;-&lt;variant&gt;`, launch localhost random port token-less,
gateway proxies ws+http, SHA-256 verified. Docs: CONTRIBUTING release
procedure, `ide/README.md`, `docs/architecture/ide.md`, README Platforms
table + size note. Done when one jar serves IDE + terminal on all six and
`slim` halves the size.

### Phase 5 — Backends

`local` default on Windows/macOS: child process in
`data/workspaces/&lt;id&gt;/`, `platform.terminal(cwd)`,
`ProcessHandle.descendants()` kill, no filesystem isolation.
`firecracker` Linux-only when `/dev/kvm` exists +
`jahaziel.workspace.backend=firecracker`. Docs: threat-model disclosure
first paragraph, `docs/architecture/workspaces.md`, README isolation column.
Done when Windows/macOS get IDE + terminal without KVM, Linux/KVM gets
isolation.

## Features (make it good)

1. **`init`:** `java -jar jahaziel.jar init` scaffolds `.env` (placeholder
   `BASIS_*` + random `BASIS_CLIENT_SECRET`) + `data/`; refuses overwrite.
2. **`doctor`:** JDK ≥ 25, writable `jarDir`, `.env` well-formed, 11451
   free (or holder), SQLite writes, REH extracts + SHA ok, `tmpdir` inside
   `data/`, no stray java. One line per check + fix hint. Exit 0 green.
3. **`upgrade --to <new.jar>`:** record version, shutdown, backup `data/`
   to `pre-upgrade-<timestamp>/`, Flyway from new jar, rollback on failure.
   `--check` prints version + SHA + last-good.
4. **`backup`/`restore`:** `data/backups/<timestamp>.tar.gz` (SQLite
   `.backup` + `workspaces/` + `.env` unless `--no-secrets`); safe restore
   with pre-snapshot; offline.
5. **Admin `/_admin`:** overview, health, workspaces, users, logs tail
   (1000 lines), config with source, diagnostics support-bundle download.
6. **Live logs:** browser + `logs --follow --level=`; JSON on disk, pretty
   browser, ANSI terminal respecting `NO_COLOR`.
7. **Templates `data/templates/`:** empty, Node 24 + pnpm, Python 3.12 +
   uv, Java 25 + wrapper, VitePress skeleton; `template.json` + setup
   commands; drop-in custom.
8. **Share links:** signed URL TTL 1h default, read-only files + terminal
   output, revocable in admin.
9. **`--demo`:** bind 11451, seed demo user/workspace, stub IdP one-click
   login, ignore `BASIS_*`, write to `data/`.
10. **Auth SPI `dev.jahaziel.auth.AuthProvider`:** `DevConnectProvider`
    default/only shipped, `StubProvider` dev/demo, third-party docs.
11. **Plugin SPI:** workspace hooks, auth hooks, gateway middleware, admin
    panels; `data/plugins/*.jar`, same process policy as workspaces.
12. **Opt-in metrics:** off by default; `Jahaziel_telemetry_optin=true`
    sends install id, version, OS/arch, coarse usage. No contents/paths/users.
13. **Support bundle:** `support-bundle` → redacted zip (allowlist).

## DX (make it pleasant)

### Phase 6 — Dev loop

`dev.ps1` (Windows) + `Makefile` (macOS/Linux), same verbs: `bootstrap`,
`build`, `build:slim`, `test`, `test:watch`, `run`, `dev` (DevTools hot
reload), `reset`, `smoke`, `docs`, `docs:build`, `release`, `clean`.
`.env.example` (five `BASIS_*`, `PORT=11451`, `JAHAZIEL_LOG_LEVEL`,
`JAHAZIEL_DEMO`, `JAHAZIEL_BACKEND`). `dev` profile
(`application-dev.yml` stub issuer, demo seed, `tools/dev-idp/` ~200-line
JWKS app). Parallel tests, per-class timing,
`docs/contributing/fast-feedback.md`. Error messages: what/why/fix +
`doctor` hint, checklist in CONTRIBUTING. Startup banner (version/host/java/
data/port/backend/ide/status). Docs: CONTRIBUTING verbs + guides,
`tools/dev-idp/README.md`, `fast-feedback.md`, `dev-setup.md`. Done when
fresh clone → `bootstrap` → `run` → 11451 login via dev-idp works.

### Phase 7 — Smoke test

`SmokeTest.java` `@Tag("smoke")`, `./mvnw -Psmoke verify`: tmp workdir,
copy jar, bare → Usage exit 0, PORT-only `.env` → non-zero naming
`BASIS_AUTH_ISSUER`, `init`, `doctor` 0/1, full run 60s
`/actuator/health` UP, `jahaziel.db` exists, workdir only jar+`.env`+`data/`,
`/` 200 contains `workbench`, `/_admin` 200, destroy < 20s, no orphan java.
Docs: CONTRIBUTING release checklist + testing,
`docs/operations/verification.md`. Done when green on all six combos.

## Documentation + site

### Phase 8 — Docs sweep + VitePress + hand-off

VitePress root `docs/`, `.vitepress/` inside. `README.md` stays as GitHub
landing, links to site. Tree: `guide/`, `architecture/`, `deployment/`
(+ `arm64-notes.md`), `operations/`, `reference/`, `security/`,
`contributing/`, `cookbook/`, `plan.md`. Node 24.18; `docs`/`docs:build`
verbs; nav mirrors tree; local search; dark default; "Last verified X" on
arch/deploy/ref docs; 6-row platform tables. Content sweep: run every
command, verify paths/snippets, remove `--run=`/`--services-dir`/`--status`/
systemd/Task Scheduler/`DESIGN.md`/`C:/tmp`; port 11451; nothing outside
`./data/`; README < 200 lines; threat-model disclosure first paragraph;
CONTRIBUTING prereqs/verbs/rules/testing/release/acceptance/errors.
Standing rule `docs/contributing/docs-contract.md`; `.githooks/pre-commit`
bans `DESIGN.md`, `--run=`, `systemd`, `Task Scheduler`, `C:/tmp`,
`Signed-off-by:`. Hand-off: confirm `build`+`test`+`smoke`+`docs:build`
green, write `docs/operations/acceptance.md` (SHA, jar SHA-256, changes,
checklist + results table), then stop and ask the human to run Phase 9.
Done when `docs:build` green, banned-string grep clean, every command run,
acceptance written, human asked.

## Manual acceptance

### Phase 9 — Human-run

Not executed by the agent. Clean machine per combo, JDK 25 only, no admin/
WSL/Docker/git/Node. Priority: Windows x64, macOS arm64, Linux x64, then
Windows arm64, macOS x64, Linux arm64 (tested/untested/failed). Checklist:
mkdir, copy jar, `init`, fill `BASIS_*` keep 11451 native endings,
`doctor` green, run, banner Host line, open 11451, DevConnect login, VS Code
workspace, terminal `dir`/`ls` + `echo hi`, `/_admin` renders, support
bundle zip, second workspace from template, share link read-only private
window, SIGINT < 5s, `data/` contents, folder exactly jar+`.env`+`data/`,
system temp/config/scheduler dirs unchanged, no java remains, `backup`
tarball, second run < 3s, space-path repeat, moved-path repeat, changed
`PORT` repeat. Docs: `docs/operations/acceptance.md` + CONTRIBUTING link.
Done when human reports pass on the three primaries + status of the rest.

## Order of execution

| # | Phase / Feature | Effort | Blocks |
|---|---|---|---|
| 1 | Phase 1 — one pom | 1 day | 3, 4, 5 |
| 2 | Phase 3 — run model | 2 days | 6, 7 |
| 3 | Phase 2 — Platform | 2 days | 4, 5 |
| 4 | Feature 1 — `init` | 1 day | onboarding |
| 5 | Feature 2 — `doctor` | 1 day | support |
| 6 | Phase 7 — smoke suite | 1 day | release |
| 7 | Phase 4 — REH (six variants, three modes) | 4–6 days | 6, 9 |
| 8 | Phase 5 — backends | 2–3 days | 6, 9 |
| 9 | Feature 5 — admin dashboard | 3 days | support |
| 10 | Feature 6 — live logs | 2 days | support |
| 11 | Features 3, 4 — upgrade/backup/restore | 2 days | ops |
| 12 | Feature 9 — demo mode | 1 day | onboarding |
| 13 | Feature 7 — templates | 2 days | onboarding |
| 14 | Feature 8 — share links | 2 days | collaboration |
| 15 | Features 10, 11 — auth + plugin SPI | 3 days | extensibility |
| 16 | Feature 12 — opt-in metrics | 1 day | telemetry |
| 17 | Phase 6 — dev loop + bootstrap | 2 days | contributor onboarding |
| 18 | Phase 8 — docs + VitePress + hand-off | 2 days | human acceptance |
| 19 | Phase 9 — human acceptance | human's time | ship |

Critical path: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 18 → 19. Final action is the
Phase 8 hand-off; the agent stops and waits.
