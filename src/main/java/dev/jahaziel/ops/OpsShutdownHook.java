/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

/** JVM shutdown hook that triggers the graceful drain with a bounded timeout. */
public final class OpsShutdownHook extends Thread {
    private final GracefulDrainService drain;
    private final long timeoutMillis;

    /** Creates a hook that drains the given service. */
    public OpsShutdownHook(GracefulDrainService drain, long timeoutMillis) {
        super("jahaziel-graceful-drain");
        if (drain == null) {
            throw new IllegalArgumentException("drain must not be null");
        }
        this.drain = drain;
        this.timeoutMillis = timeoutMillis;
    }

    /** Runs the drain once when the JVM exits. */
    @Override
    public void run() {
        drain.drain(timeoutMillis);
    }
}
