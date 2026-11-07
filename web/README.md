<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Web wrapper UI

TypeScript companion to the control-plane dashboard. Lists workspaces and
deep-links into the IDE and terminal sessions. No business logic lives
here; the REST API owns every decision.

Build: `pnpm --dir web run build`. The output ships with the control
plane static resources.
