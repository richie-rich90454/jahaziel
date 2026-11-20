<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Style guide

Short version of the repository [AGENTS.md](../../AGENTS.md) rules. The build
uses JDK 25, Node 24+, and pnpm 12.14.0; use the wrapper (`./mvnw`, `mvnw.cmd`)
and `dev.ps1` with repository-local caches. No global installs.

## Java

- New and touched Java stays at AP Computer Science A level: plain and abstract
  classes and constructs teachable in AP CSA. Prefer explicit loops and small
  methods over clever generic or functional chains. Extension points are
  abstract classes, not interfaces or records.
- Use 4-space indentation. Name things for what they do, in plain English.
- Keep feature internals behind a small public API package; ArchUnit enforces
  the `dev.jahaziel.*` boundaries. `dev.jahaziel.config` is the composition
  root and the explicit exception.

## TypeScript

- Use 4-space indentation. `web/src` is the only maintained UI source.
- Format with `oxfmt`; check with `pnpm --dir web run format:check`.

## Commits

- One cohesive behavior per commit; prefer single-file commits where the change
  allows. Stage only intended paths — never `git add .` or `git commit -a`.
- Conventional messages: `<type>(<scope>): <subject>`, imperative, lowercase,
  no period, max 72 characters. Explain *why* in the body, not *what*.
- Include the doc and test updates the change requires; see the
  [docs contract](docs-contract.md).
