<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Verification

After any deploy or upgrade, on the release hardware:

1. `http://localhost:11451/actuator/health` returns 200.
2. Log in, create a workspace, open the IDE, run a command.
3. Open a workspace port through the gateway and confirm it serves.
4. Create and restore a snapshot; confirm the files come back.
5. Confirm nothing was written outside `./data/`.

Failures carry a code plus a correlation id; look the code up in
`docs/reference/errors.md` and check the server log under
`./data/logs/`.
