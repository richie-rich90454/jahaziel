<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# IDE server build (retired)

Jahaziel no longer builds or embeds a VS Code-derived remote extension host.
The tracked `ide/` fork sources, build scripts, patch stack, and bundled REH
packaging path have been removed. Existing ignored IDE build outputs remain
untouched; normal development does not fetch or remove them.

The maintained browser workbench lives in `web/src` and is bundled into the
single jar by the repository-local Windows PowerShell workflow:

```powershell
.\dev.ps1 build
```

See [the current workbench architecture](../architecture/ide.md),
[the product plan](../plan.md), and [the development guide](../guide/quick-start.md).
