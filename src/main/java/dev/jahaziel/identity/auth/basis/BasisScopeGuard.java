/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import java.util.Set;

/** Checks parsed access-token grants. Permissions also honour a namespace-wide ".all" grant. */
public final class BasisScopeGuard {
    public void requireScopes(Set<String> granted, String... required) {
        for (int index = 0; index < required.length; index++) {
            if (granted == null || !granted.contains(required[index])) {
                throw new ScopeDeniedException(
                        "insufficient_scope", "Missing required scope: " + required[index]);
            }
        }
    }

    public void requirePermissions(Set<String> granted, String... required) {
        for (int index = 0; index < required.length; index++) {
            if (!hasPermission(granted, required[index])) {
                throw new ScopeDeniedException(
                        "insufficient_permissions",
                        "Missing required permission: " + required[index]);
            }
        }
    }

    private static boolean hasPermission(Set<String> granted, String required) {
        if (granted == null) {
            return false;
        }
        if (granted.contains(required)) {
            return true;
        }
        int dot = required.lastIndexOf('.');
        while (dot > 0) {
            if (granted.contains(required.substring(0, dot) + ".all")) {
                return true;
            }
            dot = required.lastIndexOf('.', dot - 1);
        }
        return false;
    }

    /** A valid token that lacks a required grant. Maps to the documented 403 problem response. */
    public static final class ScopeDeniedException extends BasisException {
        private static final long serialVersionUID = 1L;

        private final int status;
        private final String code;

        public ScopeDeniedException(String code, String message) {
            super(message);
            this.status = 403;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }
}
