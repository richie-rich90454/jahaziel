/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Reference to an immutable base image, for example {@code base-image:2026.09}. */
public final class ImageRef {

    private final String value;

    public ImageRef(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        this.value = value;
    }

    public String value() {
        return this.value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImageRef)) return false;
        ImageRef other = (ImageRef) o;
        return (java.util.Objects.equals(this.value, other.value));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.value);
    }

    @Override
    public String toString() {
        return "ImageRef[" + "value=" + this.value + "]";
    }
}
