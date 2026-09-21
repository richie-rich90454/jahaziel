<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Platform

`dev.jahaziel.platform` is the only package that branches on the OS.
Everything else talks to its abstractions; callers never read `os.name`.

- OS and arch detection lives here and nowhere else.
- Per-OS process, filesystem, and socket behavior is adapted here.
- The six supported combos are listed in `docs/plan.md`; a combo is
  supported only if this package names it.
