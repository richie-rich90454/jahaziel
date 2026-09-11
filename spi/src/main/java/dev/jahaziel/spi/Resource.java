/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** A resource a permission can be granted on. */
public record Resource(String type, String id) {
  public Resource {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("type must not be blank");
    }
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
  }
}
