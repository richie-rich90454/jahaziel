/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Scope a scheduled job runs in. */
public final class JobScope {
    /** One instance per cluster. */
    public static final JobScope CLUSTER = new JobScope("CLUSTER");

    /** One instance per node. */
    public static final JobScope NODE = new JobScope("NODE");

    /** One instance per workspace. */
    public static final JobScope WORKSPACE = new JobScope("WORKSPACE");

    private static final JobScope[] VALUES = {CLUSTER, NODE, WORKSPACE};

    private final String name;

    private JobScope(String name) {
        this.name = name;
    }

    /** Returns the constant name. */
    public String name() {
        return name;
    }

    /** Returns all constants. */
    public static JobScope[] values() {
        return VALUES.clone();
    }
}
