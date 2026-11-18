/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

/** Failure in the basis-auth provider. Messages never contain tokens or secrets. */
public class BasisException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BasisException(String message) {
        super(message);
    }

    public BasisException(String message, Throwable cause) {
        super(message, cause);
    }
}
