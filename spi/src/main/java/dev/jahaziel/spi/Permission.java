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
public record Permission(String name) {
  public Permission {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }
}
