/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier of a workspace.
 *
 * @param value the identifier value
 */
public record WorkspaceId(UUID value) {
  public WorkspaceId {
    Objects.requireNonNull(value, "value");
  }
}
