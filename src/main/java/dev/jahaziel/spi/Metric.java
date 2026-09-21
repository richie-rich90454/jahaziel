/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One exported measurement.
 *
 * @param name the metric name
 * @param value the measured value
 * @param tags distinguishing labels
 * @param timestamp when the measurement was taken
 */
public record Metric(String name, double value, Map<String, String> tags, Instant timestamp) {
  public Metric {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    Objects.requireNonNull(tags, "tags");
    Objects.requireNonNull(timestamp, "timestamp");
    tags = Map.copyOf(tags);
  }
}
