/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Who performed an audited action. */
public final class ActorType {
    /** A regular user. */
    public static final ActorType USER = new ActorType("USER");

    /** The platform itself. */
    public static final ActorType SYSTEM = new ActorType("SYSTEM");

    /** A node agent. */
    public static final ActorType NODE = new ActorType("NODE");

    /** A human administrator. */
    public static final ActorType ADMIN = new ActorType("ADMIN");

    private static final ActorType[] VALUES = {USER, SYSTEM, NODE, ADMIN};

    private final String name;

    private ActorType(String name) {
        this.name = name;
    }

    /** Returns the constant name. */
    public String name() {
        return name;
    }

    /** Returns all constants. */
    public static ActorType[] values() {
        return VALUES.clone();
    }

    /**
     * Returns the constant with the given name.
     *
     * @param name the constant name
     * @return the matching constant
     */
    public static ActorType valueOf(String name) {
        for (int index = 0; index < VALUES.length; index++) {
            if (VALUES[index].name.equals(name)) {
                return VALUES[index];
            }
        }
        throw new IllegalArgumentException("No enum constant ActorType." + name);
    }
}
