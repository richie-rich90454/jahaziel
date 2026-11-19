/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

/** Result of a quota or rate check, carrying the HTTP status a gateway should return. */
public final class LimitDecision {
    /** HTTP 403: a durable quota is exhausted. */
    public static final int FORBIDDEN = 403;

    /** HTTP 429: a request rate is exceeded. */
    public static final int TOO_MANY = 429;

    private final boolean allowed;
    private final int statusCode;
    private final String reason;

    private LimitDecision(boolean allowed, int statusCode, String reason) {
        this.allowed = allowed;
        this.statusCode = statusCode;
        this.reason = reason;
    }

    /** Returns an allowed decision. */
    public static LimitDecision allow() {
        return new LimitDecision(true, 200, "ok");
    }

    /** Returns a forbidden (403) decision with an actionable reason. */
    public static LimitDecision forbidden(String reason) {
        return new LimitDecision(false, FORBIDDEN, reason);
    }

    /** Returns a throttled (429) decision with an actionable reason. */
    public static LimitDecision throttled(String reason) {
        return new LimitDecision(false, TOO_MANY, reason);
    }

    /** Returns whether the operation may proceed. */
    public boolean allowed() {
        return allowed;
    }

    /** Returns the HTTP status code (200, 403 or 429). */
    public int statusCode() {
        return statusCode;
    }

    /** Returns the human-readable reason. */
    public String reason() {
        return reason;
    }
}
