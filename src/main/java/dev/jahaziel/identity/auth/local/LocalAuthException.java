/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.local;

/** Signals a rejected local authentication attempt. */
public final class LocalAuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LocalAuthException(String message) {
        super(message);
    }

    public LocalAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
