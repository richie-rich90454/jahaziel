<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Share links

Last verified against version 0.1.0-SNAPSHOT.

`POST /api/v1/workspaces/{id}/shares` mints a signed, read-only URL for a
workspace: files plus terminal output, no shell access. Links expire after
one hour and can be revoked early with `DELETE /api/v1/shares?token=…`.

Revoke and expiry are managed from the admin dashboard at
`http://localhost:11451/_admin`.
