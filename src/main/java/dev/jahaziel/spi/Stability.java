/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Stability of a plugin interface. */
public final class Stability {
    /** Stable. Breaking changes arrive as a new interface version. */
    public static final Stability STABLE = new Stability("STABLE");

    /** Beta. Usable but still allowed to change between minor releases. */
    public static final Stability BETA = new Stability("BETA");

    /** Experimental. May change or disappear at any time. */
    public static final Stability EXPERIMENTAL = new Stability("EXPERIMENTAL");

    private static final Stability[] VALUES = {STABLE, BETA, EXPERIMENTAL};

    private final String name;

    private Stability(String name) {
        this.name = name;
    }

    /** Returns the constant name. */
    public String name() {
        return name;
    }

    /** Returns all constants. */
    public static Stability[] values() {
        return VALUES.clone();
    }
}
