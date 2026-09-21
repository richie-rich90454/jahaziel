/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier of a terminal session.
 *
 * @param value the identifier value
 */
public record TerminalSessionId(UUID value) {
  public TerminalSessionId {
    Objects.requireNonNull(value, "value");
  }
}
