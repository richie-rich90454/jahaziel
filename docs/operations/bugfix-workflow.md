<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Bugfix workflow

From a production bug to a verified fix. Keep changes small, use only the
repo-local toolchain, and prove the fix on staging before it reaches
production.

1. **Reproduce** the bug. Record the steps, expected versus actual result,
   and the time it happened.
2. **Capture evidence without secrets**: the failure's error code and
   correlation id, a redacted `support-bundle`, and the matching log slice.
   See [bug report collection](./update-and-rollback.md#bug-report-collection).
3. **Write a regression test** that fails on the bug, next to the existing
   tests for that area.
4. **Fix the root cause**, not the symptom.
5. **Run tests**: first the focused class,
   `./mvnw -B test '-Dtest=<TestClass>'` (Windows: `.\mvnw.cmd ...`), then
   the full gate `./mvnw -B verify -Psmoke`.
6. **Run E2E**: `pnpm --dir web test:e2e` (Playwright against the stub).
7. **Rebuild the jar**: `target/jahaziel.jar` (`target\jahaziel.jar` on
   Windows).
8. **Deploy to staging through the same update path as production**:
   back up, `update --dry-run`, `update --to`, restart, and `doctor` per
   [Update and rollback](./update-and-rollback.md).
9. **Verify staging** with the [Verification](./verification.md) checklist.
   Only then promote the same jar to production via that same runbook.
10. **Record the result** in [Release acceptance](./acceptance.md#results)
    and the [release acceptance record](./release-acceptance.md); if a
    restore drill was involved, log it in the
    [restore drill log](./restore-drill-log.md).

Working rules:

- **One-file commits**: keep each commit small and single-purpose, ideally
  one file, so a bad change can be reverted without unrelated work. Do not
  mix the regression test, fix, and documentation into one giant commit. Use
  conventional messages (`type(scope): subject`, imperative, lowercase,
  no period) and stage only intended paths, never `git add .`.
- **No global installs**: use `./mvnw`, `pnpm --dir web`, and `.\dev.ps1`.
  JDK 25, Node 24+, and pnpm 12.14.0 are already on the machine. No
  `npm i -g`, no WSL, no system Maven.
- Never run `verify` or E2E for the first time on production. Production
  receives only the jar that is already green on staging.
