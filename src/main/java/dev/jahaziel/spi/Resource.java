/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** A resource a permission can be granted on. */
public final class Resource {

    private final String type;

    private final String id;

    public Resource(String type, String id) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.type = type;
        this.id = id;
    }

    public String type() {
        return this.type;
    }

    public String getType() {
        return type;
    }

    public String id() {
        return this.id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resource)) return false;
        Resource other = (Resource) o;
        return (java.util.Objects.equals(this.type, other.type))
                && java.util.Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.type, this.id);
    }

    @Override
    public String toString() {
        return "Resource[" + "type=" + this.type + ", " + "id=" + this.id + "]";
    }
}
