<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Error codes

Every user-facing error carries a machine-readable code, a handwritten
message, and a correlation id. Stack traces never leave the server.

| Code | HTTP | Meaning |
|---|---|---|
| `AUTH_REQUIRED` | 401 | No session token |
| `AUTH_INVALID` | 401 | Invalid session token |
| `AUTH_EXPIRED` | 401 | Session token expired |
| `FORBIDDEN` | 403 | User lacks permission |
| `NOT_FOUND` | 404 | Resource not found |
| `VALIDATION_FAILED` | 400 | Request body invalid |
| `STATE_CONFLICT` | 409 | Lifecycle state forbids the action |
| `QUOTA_EXCEEDED` | 409 | Quota exceeded |
| `ADMISSION_REFUSED` | 503 | Cannot start workspace (insufficient memory) |
| `VM_START_FAILED` | 500 | VM failed to start |
| `STORAGE_FAILED` | 500 | Storage operation failed |
| `NODE_AGENT_UNREACHABLE` | 503 | Node agent not responding |
| `RATE_LIMITED` | 429 | Rate limit exceeded |
| `INTERNAL_ERROR` | 500 | Unexpected error |
