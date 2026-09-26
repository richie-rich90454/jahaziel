<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Using Jahaziel

Last verified against version 0.1.0-SNAPSHOT.

The workbench at `http://localhost:11451` is the whole product: create a
workspace, start it, open the IDE link that appears. Everything below is what
each button and badge on that page means.

## The normal flow

1. Type a name, press **Create**. The card appears as `requested`.
2. Press **Start**. The card walks `requested → preparing → booting → ready`,
   with a progress bar for the boot sub-steps: extracting, spawning, waiting,
   registering.
3. The **first ever start takes about a minute**: it unpacks the ~108 MB IDE
   runtime. Later starts reuse it and take seconds.
4. When the card reads `ready`, an **Open IDE** link appears under it. Click
   it — that is the VS Code-compatible IDE running in your browser.

## Buttons cheat-sheet

| Card state | Buttons | Meaning |
|---|---|---|
| `requested` | Start | Begin the boot. |
| `preparing`, `booting` | Starting… (disabled), Retry start, Cancel | A boot is mid-flight. Retry start resumes it; Cancel parks it as `suspended` with files kept. |
| `ready`, `active`, `idle` | Stop, Restart | Stop parks it; Restart reboots it. |
| `suspended`, `failed` | Restart | Re-runs the boot from the preserved home folder. |
| any | Delete | Asks for confirmation. Home files are kept. |

`Cancel` during a boot is safe: it stops the IDE server and parks the
workspace as `suspended`. Nothing is deleted.

## Ports section

- `No ports registered. Start the workspace to open it in the browser.` —
  nothing is serving yet. Press Start (or Retry start).
- `IDE is starting — its link appears here when the port registers.` — the
  boot is nearly done; the Open IDE link lands within seconds.
- `:port` links — the live entry points. Open IDE opens the same target in a
  new tab.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `No live boot is reporting` on a `preparing`/`booting` card | The server restarted mid-boot (live progress is in-memory), or the boot died before reporting | Press **Retry start** |
| Card reads `failed` with a `Failed: …` reason | The boot threw; the reason names the cause and survives restarts | Press **Restart**; if it repeats, read the reason — it names the cause |
| Start spins, then `failed: IDE server exited during startup (exit=…)` | The IDE process died instantly; the card quotes the tail of its log | Read the full IDE log (below) — it holds the crash output — then Restart |
| Start spins, then the card is `failed: IDE server did not listen in time` | The IDE process exited or never bound its port within 30 s | Check the IDE log (below), then Restart |
| Start spins, then `failed: REH tarball missing …` | The IDE runtime was never staged next to the jar | Run the jar from its folder, or rebuild with the `bundled-ide` profile |
| `doctor` is not `9/9` | Environment prerequisite missing | Fix what doctor names, then start the jar |

The IDE log per workspace lives at `.jahaziel-ide/server.log` inside that
workspace's home folder (under the jar's `data/` tree). It is the first place
to look whenever a boot fails.

See [quick start](./quick-start.md) for install/run, [demo
mode](./demo-mode.md) for the zero-config path, and
[configuration](./configuration.md) for every variable.
