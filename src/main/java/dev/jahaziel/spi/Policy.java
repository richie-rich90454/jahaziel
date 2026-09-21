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
public record Policy(String name, Map<String, String> attributes) {
  public Policy {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    Objects.requireNonNull(attributes, "attributes");
    attributes = Map.copyOf(attributes);
  }
}
