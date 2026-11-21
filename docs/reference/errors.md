<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Error codes

Last verified against version 0.1.0-SNAPSHOT.

Every user-facing error carries a handwritten message and a correlation id.
Stack traces, raw exception messages, filesystem paths, tokens and cookies
never leave the server.

## RFC 9457 problem responses

API failures use `Content-Type: application/problem+json` with an RFC 9457
body:

| Field | Meaning |
|---|---|
| `type` | Problem type; `about:blank` |
| `title` | Short, fixed title such as `Forbidden` or `Bad Request` |
| `status` | HTTP status code |
| `detail` | Handwritten, actionable description |
| `instance` | Request path where applicable |
| `correlationId` | UUID linking the response to the server log |

## Application codes

| Code | HTTP | Meaning |
|---|---|---|
| `AUTH_REQUIRED` | 401 | No session token (gateway) |
| `AUTH_INVALID` | 401 | Invalid, expired or revoked session token |
| `FORBIDDEN` | 403 | User lacks permission |
| `NOT_FOUND` | 404 | Resource not found |
| `VALIDATION_FAILED` | 400 | Request body invalid |
| `STATE_CONFLICT` | 409 | Lifecycle state forbids the action |
| `FILE_CONFLICT` | 412 | File changed since it was read (`If-Match`) |
| `TERMINAL_LIMIT` | 429 | Terminal session limit reached |
| `NODE_AGENT_UNREACHABLE` | 503 | Node agent not responding |
| `INTERNAL_ERROR` | 500 | Unexpected error |

## Basis-auth token rejections

Revoked, expired, malformed, wrong-audience, wrong-issuer and non-`at+jwt`
access tokens all produce the same public response. The resource filter on
`/api/v1/workspaces/**` acts only when an `Authorization: Bearer` header
carries a JWT; rejection reasons are logged server-side only.

```http
HTTP/1.1 401
WWW-Authenticate: Bearer error="invalid_token"
Content-Type: application/json

{ "error": "invalid_token", "error_description": "Invalid access token" }
```

A valid token that lacks a grant returns `403` with error
`insufficient_scope` (missing OAuth scope) or `insufficient_permissions`
(missing app-scoped permission). A stored `<namespace>.all` permission covers
every permission below that namespace. Cookie-based app sessions may use the
application's own session error code with the same status and description.

## Numeric basis-auth codes

A callback whose `state` lacks the matching `basis_login_state` browser cookie
is rejected before the issuer is contacted; JSON clients receive HTTP `406`
because the failure is a redirect for browser clients.

Sign-in failures surface the issuer's numeric application code in the login
error body with recovery guidance. These match the downstream contract's
[troubleshooting table](../basis_auth_setup_guide.md#11-troubleshooting):

| Code | Meaning |
|---|---|
| `14001` | Client not registered |
| `14002` | Client disabled |
| `14003` | Public client sent a secret |
| `14004` | Confidential secret invalid or missing |
| `14005` | Client has no organizations |
| `14100` | Redirect URI missing or unregistered — compare the URI character-for-character |
| `14401` | Scope not allowed |
| `14407` | Resource not registered |
| `14408` | Backchannel URL is not registered or verified for the resource |
| `14429` | Unsupported response type — send `response_type=code` |
| `14501` | Resource not registered for this client |
| `2400` | Interaction missing or expired — restart authorization |
| `5004xx` | Upstream Microsoft Entra login failures, mapped by the provider |

`invalid_grant` on code exchange means the code expired, was consumed, or the
verifier was wrong; on refresh it means the token was rotated or the session
was revoked. Clear state and require a new sign-in.

Webhook verification and the internal listener use their own statuses:
`202` for `action=test`, `400 invalid_request` for bad input, `401
unauthorized` for missing or invalid resource credentials, `408 timeout` for
an expired challenge, `409` when a resource has no callback, and `502` when a
receiver is unreachable.

## Correlation ids

A `correlationId` accompanies problem responses, failed operations (for
example `STORAGE_INSPECTION_FAILED` in `doctor`), and workspace setup/task
state. Quote it in a support request together with a redacted
[support bundle](../operations/update-and-rollback.md#bug-report-collection);
never include tokens, authorization codes, secrets, or cookies.
