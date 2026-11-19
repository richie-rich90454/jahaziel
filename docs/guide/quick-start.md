<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Quick start

Last verified against version 0.1.0-SNAPSHOT.

1. Use an existing Java 25 runtime on a Linux, macOS, or Windows host (Linux x64 and arm64 are the CI-verified targets).
2. Create a folder, copy `jahaziel.jar` into it.
3. Run `java -jar jahaziel.jar init` — writes `.env` and `data/`.
4. Fill the five `BASIS_*` values in `.env` for basis-auth (or configure another provider — see [authentication providers](./configuration.md#authentication-providers); or try `--demo` first).
5. Run `java -jar jahaziel.jar doctor` and review host, writable paths, backend, and disk usage.
6. Run `java -jar jahaziel.jar` — open `http://localhost:11451`.

See [using Jahaziel](./using-jahaziel.md) for the workbench buttons and states,
[configuration](./configuration.md) for every variable and
[demo mode](./demo-mode.md) for the zero-config path.

Windows and macOS computers are browser clients and can also host the jar. For a supervised install, use the [service templates per OS](../deployment/single-server.md#service-templates-per-os); native macOS and Windows host acceptance is still open.
