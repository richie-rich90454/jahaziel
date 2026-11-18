/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.token;

/** Failure in the API token provider. Messages never contain raw token values. */
public class ApiTokenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ApiTokenException(String message) {
        super(message);
    }

    public ApiTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
