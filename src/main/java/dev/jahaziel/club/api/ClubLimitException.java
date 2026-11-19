/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.api;

/** Thrown when a session capacity or rate limit is exceeded. */
public class ClubLimitException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the reason, safe to show to the caller
     */
    public ClubLimitException(String message) {
        super(message);
    }
}
