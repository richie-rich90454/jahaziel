/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Policy decision for an extension request. */
public final class Decision {
    /** The extension may be installed. */
    public static final Decision ALLOW = new Decision("ALLOW");

    /** The extension must not be installed. */
    public static final Decision BLOCK = new Decision("BLOCK");

    /** Installation waits for an administrator approval. */
    public static final Decision REQUIRE_APPROVAL = new Decision("REQUIRE_APPROVAL");

    private static final Decision[] VALUES = {ALLOW, BLOCK, REQUIRE_APPROVAL};

    private final String name;

    private Decision(String name) {
        this.name = name;
    }

    /** Returns the constant name. */
    public String name() {
        return name;
    }

    /** Returns all constants. */
    public static Decision[] values() {
        return VALUES.clone();
    }
}
