/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Duration;

/** A periodic platform job. */
public abstract class PlatformJob {
    /**
     * Runs once.
     *
     * @param context the run context
     */
    public abstract void run(JobContext context);

    /**
     * Returns how often the job runs.
     *
     * @return the period
     */
    public abstract Duration period();

    /**
     * Returns where the job runs.
     *
     * @return the scope
     */
    public abstract JobScope scope();
}
