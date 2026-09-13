<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# IDE smoke tests

Open, edit, save against a built server. Each script exits nonzero on
failure; CI runs the whole directory after every patch-stack change.

| Script | Covers |
|---|---|
| `open-edit-save.sh` | Workbench opens, file edits persist |
