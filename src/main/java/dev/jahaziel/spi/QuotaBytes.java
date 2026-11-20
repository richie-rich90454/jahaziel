/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Logical storage quota in bytes. Only actual consumption matters physically. */
public final class QuotaBytes {

    private final long value;

    public QuotaBytes(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
        this.value = value;
    }

    public long value() {
        return this.value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuotaBytes)) return false;
        QuotaBytes other = (QuotaBytes) o;
        return (this.value == other.value);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.value);
    }

    @Override
    public String toString() {
        return "QuotaBytes[" + "value=" + this.value + "]";
    }
}
