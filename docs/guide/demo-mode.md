<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Demo mode

Last verified against version 0.1.0-SNAPSHOT.

`java -jar jahaziel.jar --demo` binds `11451`, uses a stub identity
provider, seeds a demo user and a demo workspace, and ignores the
`BASIS_*` requirements. Everything still lands in `./data/`.

The workbench shell at `/` and the admin dashboard at `/_admin` are open
in demo mode with no login. This is a localhost try-before-you-configure
story: never use `--demo` with real data or on a shared machine.
