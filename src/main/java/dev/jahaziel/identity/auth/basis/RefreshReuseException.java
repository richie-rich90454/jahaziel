/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

/** Raised when a consumed refresh token is presented again; the session family is revoked. */
public final class RefreshReuseException extends BasisException {
    private static final long serialVersionUID = 1L;

    public RefreshReuseException() {
        super("refresh token reuse detected; session family revoked");
    }
}
