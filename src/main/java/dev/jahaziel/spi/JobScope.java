/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Scope a scheduled job runs in. */
public enum JobScope {
  /** One instance per cluster. */
  CLUSTER,
  /** One instance per node. */
  NODE,
  /** One instance per workspace. */
  WORKSPACE
}
