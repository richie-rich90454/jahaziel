/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Reference to an immutable base image, for example {@code base-image:2026.09}. */
public record ImageRef(String value) {
  public ImageRef {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }
}
