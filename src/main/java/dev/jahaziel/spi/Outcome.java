/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Outcome of an audited action. */
public final class Outcome {
    /** The action completed. */
    public static final Outcome SUCCESS = new Outcome("SUCCESS");

    /** The action failed. */
    public static final Outcome FAILURE = new Outcome("FAILURE");

    /** The action was refused by policy. */
    public static final Outcome DENIED = new Outcome("DENIED");

    private static final Outcome[] VALUES = {SUCCESS, FAILURE, DENIED};

    private final String name;

    private Outcome(String name) {
        this.name = name;
    }

    /** Returns the constant name. */
    public String name() {
        return name;
    }

    /** Returns all constants. */
    public static Outcome[] values() {
        return VALUES.clone();
    }

    /**
     * Returns the constant with the given name.
     *
     * @param name the constant name
     * @return the matching constant
     */
    public static Outcome valueOf(String name) {
        for (int index = 0; index < VALUES.length; index++) {
            if (VALUES[index].name.equals(name)) {
                return VALUES[index];
            }
        }
        throw new IllegalArgumentException("No enum constant Outcome." + name);
    }
}
