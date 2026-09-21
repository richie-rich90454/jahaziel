/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.domain;

import java.time.Instant;
import java.util.Objects;

/** Runtime state of a virtual machine. */
public sealed interface VirtualMachineState {
  /** Running on a host. */
  record Running(Instant at, String hostname) implements VirtualMachineState {
    public Running {
      Objects.requireNonNull(at, "at");
      if (hostname == null || hostname.isBlank()) {
        throw new IllegalArgumentException("hostname must not be blank");
      }
    }
  }

  /** Stopped, no guest running. */
  record Stopped(Instant at) implements VirtualMachineState {
    public Stopped {
      Objects.requireNonNull(at, "at");
    }
  }

  /** Crashed while running. */
  record Crashed(Instant at, String reason) implements VirtualMachineState {
    public Crashed {
      Objects.requireNonNull(at, "at");
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("reason must not be blank");
      }
    }
  }
}
