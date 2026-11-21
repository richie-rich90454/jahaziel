<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Restore drill log

| Date | Scope | Result | Signed off by |
|---|---|---|---|
| TO BE FILLED | TO BE FILLED (host + data directory) | TO BE FILLED AFTER A REAL DRILL: archive used, restored paths, fresh-boot readback result, and any deviations | TO BE FILLED |

First drill is due the first Saturday after production cutover. Record only a
real restore on the target host. The scratch-directory restores performed by
`tools/deployment-acceptance.cjs` in CI are regression checks, not an in-place
host drill, and must not be logged here as a completed drill.
