<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Browser matrix suite

The web client is exercised against Safari 16+, iOS Safari 16+, iPadOS
Safari 16+, Chrome 110+, Firefox 110+, and Edge 110+. The runner uses
Playwright with pinned browser revisions; every spec opens the workbench,
types in the editor, runs a terminal command, and resizes to tablet
portrait and landscape.

Run: `npm run test:browser` inside `ide/tests/browser/`.
