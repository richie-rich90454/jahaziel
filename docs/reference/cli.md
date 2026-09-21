<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# CLI reference

## init

```bash
java -jar jahaziel.jar init
```

Writes a starter `.env` beside the jar. Edit it with a text editor,
then start the server. Login stays off until `.env` holds all
required values.

## run

```bash
java -jar jahaziel.jar
```

Serves `http://localhost:11451`. The first run creates `./data/`
beside the jar; see `docs/deployment/single-server.md` for the layout.
A bare run with no `.env` prints help and runs nothing; run `init` first.

## Offline verbs

`doctor` (pre-boot checks), `upgrade --to <jar>` / `--check`,
`backup [--no-secrets]` / `restore <file>`, `logs [--follow]
[--level=LEVEL]`, `support-bundle`, and `--demo` (stub IdP, ignores
`BASIS_*`) all run without starting the server.

## Errors

Failures print a handwritten message with a machine-readable code and
a correlation id; see `docs/reference/errors.md`.
