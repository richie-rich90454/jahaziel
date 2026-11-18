/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

/** The user denied the authorization request. Callers must not retry automatically. */
public final class BasisLoginCancelledException extends BasisException {
    private static final long serialVersionUID = 1L;

    public BasisLoginCancelledException() {
        super("basis-auth login was cancelled");
    }
}
