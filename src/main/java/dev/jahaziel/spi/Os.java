/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Host operating system. Firecracker and cgroups exist on Linux only; everything else is local. */
public final class Os {
    /** Linux, the production target. */
    public static final Os LINUX = new Os("LINUX");

    /** Windows and Windows Server. */
    public static final Os WINDOWS = new Os("WINDOWS");

    /** macOS. */
    public static final Os MAC = new Os("MAC");

    /** Anything else. */
    public static final Os OTHER = new Os("OTHER");

    private static final Os[] VALUES = {LINUX, WINDOWS, MAC, OTHER};

    private final String name;

    private Os(String name) {
        this.name = name;
    }

    /** Returns the constant name. */
    public String name() {
        return name;
    }

    /** Returns all constants. */
    public static Os[] values() {
        return VALUES.clone();
    }

    /**
     * Returns whether workspace virtualization can run here.
     *
     * @return true on Linux only
     */
    public boolean supportsVirtualization() {
        return this == LINUX;
    }
}
