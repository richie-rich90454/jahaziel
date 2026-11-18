/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** Workspace collaboration roles and share scopes as plain string constants. */
public final class Roles {
    public static final String OWNER = "OWNER";
    public static final String EDITOR = "EDITOR";
    public static final String VIEWER = "VIEWER";
    public static final String SCOPE_VIEW = "VIEW";
    public static final String SCOPE_EDIT = "EDIT";

    private Roles() {}

    public static boolean isRole(String role) {
        return OWNER.equals(role) || EDITOR.equals(role) || VIEWER.equals(role);
    }

    public static boolean isScope(String scope) {
        return SCOPE_VIEW.equals(scope) || SCOPE_EDIT.equals(scope);
    }

    /** Returns 3 for owner, 2 for editor, 1 for viewer, and 0 for anything else. */
    public static int rank(String role) {
        if (OWNER.equals(role)) {
            return 3;
        }
        if (EDITOR.equals(role)) {
            return 2;
        }
        if (VIEWER.equals(role)) {
            return 1;
        }
        return 0;
    }

    /** True when the role may edit workspace content. */
    public static boolean canEdit(String role) {
        return rank(role) >= rank(EDITOR);
    }
}
