/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

/** Failure in the passkey provider. Messages never contain challenges, keys, or signatures. */
public class PasskeyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PasskeyException(String message) {
        super(message);
    }

    public PasskeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
