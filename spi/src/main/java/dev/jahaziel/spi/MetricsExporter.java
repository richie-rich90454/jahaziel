/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Exports measurements to a monitoring backend. */
@JahazielSpi(version = 1)
public interface MetricsExporter {
  /**
   * Returns the exporter name.
   *
   * @return the name, for example {@code prometheus}
   */
  String name();

  /**
   * Exports one measurement.
   *
   * @param metric the measurement to export
   */
  void export(Metric metric);
}
