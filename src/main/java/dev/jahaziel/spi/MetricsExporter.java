/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Exports measurements to a monitoring backend. */
@JahazielSpi(version = 1)
public abstract class MetricsExporter {
    /**
     * Returns the exporter name.
     *
     * @return the name, for example {@code prometheus}
     */
    public abstract String name();

    /**
     * Exports one measurement.
     *
     * @param metric the measurement to export
     */
    public abstract void export(Metric metric);
}
