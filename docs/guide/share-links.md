<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Share links

Last verified against version 0.1.0-SNAPSHOT.

`POST /api/v1/workspaces/{id}/shares` mints a signed share token for a
workspace the caller may read. Links expire after one hour. Redeeming
`GET /s/{token}` requires an authenticated session and returns only the
workspace ID and expiry; it does not by itself grant workspace file or
terminal access.

Revocation is kept in an in-memory set and is lost when Jahaziel restarts.
The signing secret is generated in memory unless `JAHAZIEL_SHARE_SECRET`
(`jahaziel.share.secret`) is configured; set it if links must survive
restarts. There is no admin revoke UI; a user with read access to the
workspace can revoke a token with `DELETE /api/v1/shares?token=…`.
