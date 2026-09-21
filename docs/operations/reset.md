<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Reset

To return to a fresh install:

1. Stop the jar.
2. Delete the `./data/` folder beside the jar.
3. Run `java -jar jahaziel.jar init` to scaffold a fresh `.env`.
4. Start with `java -jar jahaziel.jar`.

Reset is destructive and irreversible: workspaces, snapshots, audit
history, and keys are gone. Take a backup first; see
`docs/operations/backup.md`.
