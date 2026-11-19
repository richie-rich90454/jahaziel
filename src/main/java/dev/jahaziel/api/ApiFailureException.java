/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

/** API-boundary failure carrying an HTTP status and a fixed, safe detail string. */
public final class ApiFailureException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String title;
    private final String detail;

    private ApiFailureException(int status, String title, String detail) {
        super(title);
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    /** Rejects a missing or malformed principal. */
    public static ApiFailureException unauthorized() {
        return new ApiFailureException(401, "Unauthorized", "Authentication is required");
    }

    /** Rejects an authenticated caller who lacks a workspace or admin permission. */
    public static ApiFailureException forbidden() {
        return new ApiFailureException(403, "Forbidden", "You may not perform this action");
    }

    /** Rejects invalid input, including a path that escapes its workspace root. */
    public static ApiFailureException badRequest(String safeDetail) {
        return new ApiFailureException(400, "Bad Request", safeDetail);
    }

    /** Reports a state conflict such as a stale version or a locked session. */
    public static ApiFailureException conflict(String safeDetail) {
        return new ApiFailureException(409, "Conflict", safeDetail);
    }

    /** Reports a rate limit. */
    public static ApiFailureException rateLimited(String safeDetail) {
        return new ApiFailureException(429, "Too Many Requests", safeDetail);
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }
}
