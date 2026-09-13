<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Error codes

Every user-facing error carries a machine-readable code, a handwritten
message, and a correlation id. Stack traces never leave the server.

| Code | HTTP | Meaning |
|---|---|---|
| `AUTH_REQUIRED` | 401 | No session token (gateway) |
| `AUTH_INVALID` | 401 | Invalid session token |
| `FORBIDDEN` | 403 | User lacks permission |
| `NOT_FOUND` | 404 | Resource not found |
| `VALIDATION_FAILED` | 400 | Request body invalid |
| `STATE_CONFLICT` | 409 | Lifecycle state forbids the action |
| `NODE_AGENT_UNREACHABLE` | 503 | Node agent not responding |
| `INTERNAL_ERROR` | 500 | Unexpected error |
