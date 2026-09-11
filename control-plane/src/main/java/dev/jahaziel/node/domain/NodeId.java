/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import java.util.Objects;

/**
 * Stable identifier of a node.
 *
 * @param value the identifier value
 */
public record NodeId(String value) {
  public NodeId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }
}
