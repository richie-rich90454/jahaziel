/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

/** Failure in an OIDC provider. Messages never contain tokens or secrets. */
public class OidcException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OidcException(String message) {
        super(message);
    }

    public OidcException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The user denied the authorization request. Callers must not retry automatically. */
    public static final class Cancelled extends OidcException {
        private static final long serialVersionUID = 1L;

        public Cancelled(String provider) {
            super(provider + " login was cancelled");
        }
    }
}
