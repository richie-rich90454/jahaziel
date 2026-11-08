<!-- Copyright 2026 The Jahaziel Authors. SPDX-License-Identifier: Apache-2.0. -->

# Linux remote source IDE: product contract and delivery plan

## Product contract

Jahaziel is a self-hosted, browser-based source IDE for trusted users who need to work on code stored on a Linux server. Windows, macOS, and Linux desktops are browser clients. Jahaziel server releases target Linux x64 and Linux arm64; native Windows and macOS servers are deferred.

The release workflow is: sign in → create a workspace from a server-managed template → edit and save files → open a terminal → run a task → preview a port → restart the server and recover files. The first release is a modular monolith in one executable jar, on one application port, with persistent state below the configured data directory.

The workbench uses CodeMirror 6 and xterm.js as same-origin browser assets. It does not embed VS Code, download REH sources, or bundle language toolchains. Language packs are pinned, shared, and fetched only after an explicit user or administrator action. Runtime processes run as the Jahaziel service account. This mode is appropriate for trusted users and does not claim cross-user process or filesystem isolation.

## Development and disk constraints

- Develop on native Windows PowerShell. Do not use WSL, install global toolchains, change machine PATH, or register services.
- Bootstrap checks for existing JDK 25, Node 24+, and the pinned pnpm package manager. It must report missing requirements without installing them.
- Maven and pnpm dependency stores, wrapper downloads, and generated frontend outputs live under ignored repository-local directories. `./dev.ps1 cache:report` reports these sizes; `./dev.ps1 cache:prune` is dry-run by default and only removes known generated caches with `--apply`.
- Do not fetch VS Code sources, language packs, compilers, interpreters, or runtime images during app builds. Do not touch existing ignored IDE build outputs.
- A change is one cohesive behavior, including the code, documentation, and focused verification that make it reviewable.

## Architecture boundaries

- Keep one Spring Boot jar and the existing `dev.jahaziel.*` package seams. Preserve ArchUnit checks. Add Maven modules or deployable services only when a measured constraint requires them.
- Select one runtime backend at startup. Lifecycle orchestration depends on the `NodeBackend` contract, not concrete implementation types.
- Give every workspace its own `data/workspaces/<workspace-id>/home` directory. Preserve stored workspace identities and user data through additive migrations.
- Create native processes only for explicitly requested terminals and run tasks. Track their lifecycle and clean them up when a session closes or a workspace stops.
- Keep all application-owned persistent data in the configured data directory. No startup update, implicit language-pack prefetch, or automatic user-data cleanup.
- Keep authorization checks at each workspace file, terminal, language-service, task, and preview boundary. Reject traversal and symlink escapes after canonical path resolution.

## Delivery sequence

### 1. Build and release foundation

1. Pin the frontend dependencies and commit a lockfile. Build only `web/src` into static assets and package those assets into the jar; remove the separate hand-maintained browser JavaScript.
2. Make Maven and pnpm stores local to `.cache/`, ignored by Git, and report their sizes. Bootstrap performs version checks only.
3. Add native Linux x64 and arm64 CI for formatting, licensing, ArchUnit, focused behavior tests, frontend typecheck/build, jar packaging, and packaged-jar API and browser smoke flows.
4. Print jar and frontend output sizes in CI and reject bundles above the documented budget or containing IDE servers/toolchains. Verify `x86_64` and `aarch64` runner architecture directly; require both jobs to pass before release.
5. Replace the global 100% coverage gate with tests for behavior and invariants: authorization, filesystem confinement, save conflicts, terminal lifecycle, process allowlists, and recovery.

### 2. Runtime lifecycle and storage

1. Remove REH extraction and IDE process startup from normal startup and workspace lifecycle paths. Keep the existing ignored upstream IDE build artifacts untouched.
2. Keep one local backend implementation. Starting a workspace prepares its home and changes lifecycle state; it does not launch `sleep infinity`, a ping process, or an undocumented runtime descriptor.
3. Persist one home per workspace. Migrate legacy owner-shared homes conservatively, retain the source until migration is verified, and expose an actionable failure instead of silently resetting a workspace.
4. Reconcile persisted `preparing`, `booting`, and stale live states at startup. Mark interrupted operations recoverable, clear transient sessions, and report a correlation identifier and reason.
5. Describe native process mode plainly in the threat model. Verify Firecracker as a separate backend; do not imply that KVM is configured merely because its backend class exists.

### 3. Workspace creation and files

1. Extend server-owned template manifests with starter files, ports, supported language packs, setup steps, and named run tasks. Ship empty, Node/TypeScript, Python, Java, C/C++, Go, and Rust starters.
2. Validate the entire template manifest before creating a workspace: template name, file count and bytes, relative paths, unique valid ports, task IDs, language-pack IDs, and bounded resource values.
3. Apply a template by creating a workspace-specific home, writing initial files, registering its ports, and recording setup state and logs. Failures retain files and setup status; retry targets the same workspace.
4. Add authenticated workspace-rooted file list/read/save/create/rename/delete operations. Reads return a strong content ETag; existing-file saves require `If-Match`; create-only writes require `If-None-Match: *`.
5. Reject absolute paths, parent traversal, malformed path segments, symlink escapes, and invalid UTF-8/binary/oversized text files. Every operation checks the caller's workspace permission.

### 4. Browser workbench

1. Build a compact workspace chooser and template picker. Show lifecycle state, setup progress, and actionable failure details.
2. Add an explorer with lazy folder expansion, file creation, rename, delete, and a text editor with language modes, tabs, dirty indicators, save status, and conflict recovery.
3. Add task controls with selected task, live bounded output, exit code, stop action, and retry where allowed.
4. Add a resizable terminal panel using xterm.js. Create its terminal session only when requested; close its server session when the tab or workspace closes.
5. Add preview links using the existing workspace-scoped gateway. Show port registration and listening state, and keep preview authorization scoped to the workspace.
6. Provide bounded workspace search and quick-open, then Git status/diff/stage/unstage/commit for existing repositories. Cloning and credential setup stay in the terminal. Optional language-server diagnostics are connected in the language-tools delivery stage below.

### 5. Terminal and process boundary

1. Preserve the REST create/list/close routes and add `WS /api/v1/terminals/{sessionId}/stream` for input, output, resize, and process exit.
2. Authorize the WebSocket handshake, bind it to one previously authorized terminal session and workspace, and never accept an executable path or command vector from the browser.
3. Use a fixed server-selected shell, the workspace home as working directory, an environment allowlist, a bounded output replay buffer, input/output chunk limits, terminal dimension limits, and global/per-workspace session caps.
4. Track process trees. Stop them on terminal close, workspace stop/delete, or graceful server shutdown. On restart, detect or reap stale owned processes when their identity can be verified safely.
5. Redact secrets from task/terminal logs and errors. Avoid logging terminal content or inherited application credentials.

### 6. Language tools

1. Publish a server-managed, pinned catalog for clangd, JDT language server, gopls, rust-analyzer, TypeScript, and Pyright.
2. Store immutable verified versions once under `data/language-packs/`. Show version, checksum, and download size before fetch. Do not prefetch at startup or during template application unless explicitly requested.
3. Keep managed tools outside system PATH. A dry-run prune reports reclaimable versions and never removes a version referenced by a workspace.
4. Connect browser diagnostics through authenticated JSON-RPC WebSockets. The client can choose only an allowed language ID; server configuration selects the executable and arguments.
5. For the first release, provide clangd on Linux x64. Arm64 acceptance uses another available cross-architecture pack; defer C/C++ diagnostics on arm64 until a small pinned artifact can be managed without a distribution-specific package or the full LLVM archive.

### 7. Operations and upgrades

1. `doctor` reports the selected backend, host target, writable data paths, total/application/workspace/language-pack usage, optional packs, and dependencies required by the selected backend.
2. Add cache usage and a dry-run cache prune. Standard startup never updates tools or deletes caches or user data.
3. Record operation correlation IDs, bounded logs, setup/task state, and recovery guidance in the UI and server logs.
4. Use additive Flyway migrations. Preserve workspace IDs, workspace homes, and user-created files across upgrades.
5. Document server/client platforms, trusted-process limitations, backup/restore, and language-pack cache behavior from the same contract.

## Compatibility and API shape

- Keep existing `/api/v1` workspace, template, terminal, and port routes compatible where practical.
- Add files at `/api/v1/workspaces/{id}/files`; return an ETag and require it for updates to existing files.
- Add the terminal stream at `WS /api/v1/terminals/{sessionId}/stream`.
- Add setup and task status reads keyed by the existing workspace identity. A retry must not create a second workspace.
- Add allowed language sessions at `WS /api/v1/workspaces/{id}/language-services/{languageId}`.
- Store toolchains under `data/`; keep them out of the jar and the development machine.

## Acceptance gates

1. On the Windows development machine, bootstrap and build use existing JDK/Node/package-manager runtimes, native PowerShell, and only repository-local dependency stores. No WSL, PATH edits, global toolchain installs, or service installation occur.
2. Native Linux x64 and arm64 CI build and launch the packaged jar. The arm64 job must run on a native `aarch64` runner and pass before a release is signed off.
3. The packaged browser workflow covers login, template materialization, setup status, file edit/save/conflict, an explicitly enabled language diagnostic, terminal stream/resize/close, run task output/exit/stop, preview authorization, and restart recovery. After an abrupt server stop during a running task, the workbench restores its latest status, bounded output, and interruption reason, and startup stops only the recorded process session.
4. Security verification covers unauthenticated and cross-workspace access, traversal, symlink escape, WebSocket authorization, command allowlisting, environment filtering, and secret redaction.
5. Disk verification shows no VS Code or language packs fetched by a normal build, no toolchains in the jar, accurate cache reporting, and a prune dry-run with no modifications.

## Explicitly deferred

Native Windows/macOS servers; debugger parity; VS Code extension compatibility; extension marketplace; real-time collaborative editing; hostile multi-tenant isolation; automatic language-pack downloads; automatic tool/cache cleanup; and Firecracker release claims before native verification.
