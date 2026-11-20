/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Effective policy for a user on a resource.
 *
 * @param name the policy name
 * @param attributes policy attributes as plain string pairs
 */
public final class Policy {

    private final String name;

    private final Map<String, String> attributes;

    public Policy(String name, Map<String, String> attributes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(attributes, "attributes");
        attributes = Map.copyOf(attributes);
        this.name = name;
        this.attributes = attributes;
    }

    public String name() {
        return this.name;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> attributes() {
        return this.attributes;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Policy)) return false;
        Policy other = (Policy) o;
        return (java.util.Objects.equals(this.name, other.name))
                && java.util.Objects.equals(this.attributes, other.attributes);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.name, this.attributes);
    }

    @Override
    public String toString() {
        return "Policy[" + "name=" + this.name + ", " + "attributes=" + this.attributes + "]";
    }
}
