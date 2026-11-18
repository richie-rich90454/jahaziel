# Playwright E2E suite (Windows)

Self-contained browser tests for the Jahaziel web workbench. No Java, no packaged
jar, no global installs, and no Linux-only tools are required.

## Run

From the repository root:

```powershell
pnpm --dir web test:e2e
```

List tests without starting any server or browser:

```powershell
pnpm --dir web exec playwright test --list
```

Run one file or one test:

```powershell
pnpm --dir web exec playwright test tests/e2e/02-files-editor.spec.ts
pnpm --dir web exec playwright test -g "ETag conflict"
```

Chromium is used through an already-installed Chrome/Edge channel when present;
otherwise Playwright's bundled Chromium is used. If neither exists, install the
browser once with `pnpm --dir web exec playwright install chromium` (local, not
a global install).

## What it starts

One loopback-only webServer process, owned by Playwright:

1. `vite build` compiles the app into an OS-temp directory.
2. `stub-server.cjs` serves that build and implements the `/api/v1/*` REST
   surface, ETag-based file saves, task runs, Git, language packs, OpenCode
   status, and raw WebSocket streams for terminals, DAP, and LSP.

State is entirely in memory and per-run; nothing is written to the repository.

Set `E2E_REUSE_SERVER=1` to point the suite at an already-running stub instead
of building and starting one (useful for debugging the stub itself).

## Cleanup guarantees

- Every test gets a fresh browser context. `afterEach` closes extra pages and
  the context explicitly, dropping all WebSockets and PTY streams; Playwright
  then closes the browser.
- The stub keeps no files on disk. `global-teardown.cjs` removes the OS-temp
  build scratch and trace output directories.
- `trace` and `screenshot` are off. The `list` reporter writes no report files,
  so there is nothing for global teardown to race with. Only the OS-temp build
  scratch directory is removed in teardown; the Playwright output dir is left
  alone because the reporter flushes after teardown.
- The webServer process is killed by Playwright when the run ends. No
  `taskkill`, `wmic`, or POSIX process tooling is used.

## Known app-side workarounds

- `fixtures.ts` aliases the stale id `debug-config-status` to
  `debug-launch-config-status` via `addInitScript`. The SolidJS sidebar still
  binds to the old id, which throws in `byId()` and prevents `main()` from
  loading the identity. Remove the shim once `src/components/sidebar.tsx` is
  fixed.
- The home creation test forces one sidebar refresh because the new app can
  show the workbench before its `files` state is committed.

## Selector strategy

Tests use stable element IDs (`#create-workspace`, `#save-file`, `#terminal-new`,
etc.), accessible roles/labels, and visible text. Class names such as
`.tree-item` or `.preview-chip` are used only for repeated dynamic rows. The
selectors were verified against both the legacy DOM and the SolidJS/DaisyUI
component tree that keeps those IDs.
