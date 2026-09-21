/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import java.time.Instant;
import java.util.Objects;

/** Health state of a node. */
public sealed interface NodeState {
  /** Healthy and accepting placements. */
  record Healthy(Instant at) implements NodeState {
    public Healthy {
      Objects.requireNonNull(at, "at");
    }
  }

  /** Degraded but accepting placements. */
  record Degraded(Instant at, String reason) implements NodeState {
    public Degraded {
      Objects.requireNonNull(at, "at");
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("reason must not be blank");
      }
    }
  }

  /** Draining, no new placements. */
  record Draining(Instant at) implements NodeState {
    public Draining {
      Objects.requireNonNull(at, "at");
    }
  }

  /** Unhealthy, no placements. */
  record Unhealthy(Instant at, String reason) implements NodeState {
    public Unhealthy {
      Objects.requireNonNull(at, "at");
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("reason must not be blank");
      }
    }
  }
}
