/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.ldap;

/** Failure in the LDAP provider. Messages never contain passwords or directory data. */
public class LdapException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LdapException(String message) {
        super(message);
    }

    public LdapException(String message, Throwable cause) {
        super(message, cause);
    }
}
