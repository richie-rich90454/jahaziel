<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# IDE smoke tests

Open, edit, save, terminal command, git commit, install from Open VSX, run
a debug session. Each script exits nonzero on failure; CI runs the whole
directory after every patch-stack change.

| Script | Covers |
|---|---|
| `open-edit-save.sh` | Workbench opens, file edits persist |
| `terminal-git.sh` | Terminal runs, git commits |
| `marketplace.sh` | Extension installs from Open VSX |
| `debug.sh` | Debug session starts and stops |
