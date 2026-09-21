/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Duration;

/** A periodic platform job. */
public interface PlatformJob {
  /**
   * Runs once.
   *
   * @param context the run context
   */
  void run(JobContext context);

  /**
   * Returns how often the job runs.
   *
   * @return the period
   */
  Duration period();

  /**
   * Returns where the job runs.
   *
   * @return the scope
   */
  JobScope scope();
}
