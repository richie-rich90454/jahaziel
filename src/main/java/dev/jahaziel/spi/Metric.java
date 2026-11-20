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
public final class Metric {

    private final String name;

    private final double value;

    private final Map<String, String> tags;

    private final Instant timestamp;

    public Metric(String name, double value, Map<String, String> tags, Instant timestamp) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(timestamp, "timestamp");
        tags = Map.copyOf(tags);
        this.name = name;
        this.value = value;
        this.tags = tags;
        this.timestamp = timestamp;
    }

    public String name() {
        return this.name;
    }

    public String getName() {
        return name;
    }

    public double value() {
        return this.value;
    }

    public double getValue() {
        return value;
    }

    public Map<String, String> tags() {
        return this.tags;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Instant timestamp() {
        return this.timestamp;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Metric)) return false;
        Metric other = (Metric) o;
        return (java.util.Objects.equals(this.name, other.name))
                && this.value == other.value
                && java.util.Objects.equals(this.tags, other.tags)
                && java.util.Objects.equals(this.timestamp, other.timestamp);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.name, this.value, this.tags, this.timestamp);
    }

    @Override
    public String toString() {
        return "Metric["
                + "name="
                + this.name
                + ", "
                + "value="
                + this.value
                + ", "
                + "tags="
                + this.tags
                + ", "
                + "timestamp="
                + this.timestamp
                + "]";
    }
}
