/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque handle to a running workspace runtime.
 *
 * @param value the runtime identifier
 */
public record RuntimeHandle(UUID value) {
  public RuntimeHandle {
    Objects.requireNonNull(value, "value");
  }
}
