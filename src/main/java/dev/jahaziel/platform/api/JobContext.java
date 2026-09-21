/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Context a scheduled job runs with.
 *
 * @param firedAt when the run fired
 */
public record JobContext(Instant firedAt) {
  public JobContext {
    Objects.requireNonNull(firedAt, "firedAt");
  }

  /** Returns a context for right now. */
  public static JobContext now() {
    return new JobContext(Instant.now());
  }
}
