/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/**
 * A named permission that can be granted on a resource.
 *
 * @param name the permission name, for example {@code workspace.read}
 */
public final class Permission {

    private final String name;

    public Permission(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission)) return false;
        Permission other = (Permission) o;
        return (java.util.Objects.equals(this.name, other.name));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.name);
    }

    @Override
    public String toString() {
        return "Permission[" + "name=" + this.name + "]";
    }
}
