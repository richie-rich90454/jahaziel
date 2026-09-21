/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier of a virtual machine.
 *
 * @param value the identifier value
 */
public record VirtualMachineId(UUID value) {
  public VirtualMachineId {
    Objects.requireNonNull(value, "value");
  }
}
