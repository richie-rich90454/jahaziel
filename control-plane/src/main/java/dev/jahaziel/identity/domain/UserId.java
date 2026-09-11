/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier of a user.
 *
 * @param value the identifier value
 */
public record UserId(UUID value) {
  public UserId {
    Objects.requireNonNull(value, "value");
  }
}
