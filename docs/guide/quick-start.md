<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Quick start

Last verified against version 0.1.0-SNAPSHOT.

1. Install JDK 25. Nothing else is required.
2. Create a folder, copy `jahaziel.jar` into it.
3. Run `java -jar jahaziel.jar init` — writes `.env` and `data/`.
4. Fill the five `BASIS_*` values in `.env` (or try `--demo` first).
5. Run `java -jar jahaziel.jar doctor` — expect `9/9`.
6. Run `java -jar jahaziel.jar` — open `http://localhost:11451`.

See [configuration](./configuration.md) for every variable and
[demo mode](./demo-mode.md) for the zero-config path.
