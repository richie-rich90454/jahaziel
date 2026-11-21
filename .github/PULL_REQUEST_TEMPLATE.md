<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Pull request

## Change

<!-- One cohesive behavior. Include related code, docs, and tests in focused commits. -->

## Why

<!-- Link the issue this addresses: Fixes #... -->

## Verification

- [ ] `./mvnw -B verify -Psmoke` passes (or `mvnw.cmd -B verify -Psmoke` on Windows)
- [ ] Browser changes: `pnpm --dir web run build` and `cd web && pnpm exec playwright test` pass
- [ ] New behavior has a focused test
- [ ] Docs updated when the contract, configuration, or workflow changed
- [ ] No new top-level dependency without an ADR

## Checklist

- [ ] SPDX header on every new file
- [ ] Conventional commit messages (imperative, lowercase, max 72 characters)
- [ ] No user-facing stack traces or secrets in logs/screenshots

<!-- New here? Pick an issue labeled "good first issue" and ask for help in the PR if you need it. -->
