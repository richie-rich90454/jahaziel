/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

/** How a snapshot was triggered. */
public enum SnapshotKind {
  /** Platform-managed daily snapshot. Does not count against quota. */
  AUTO,
  /** User-triggered snapshot. Counts against quota. */
  MANUAL,
  /** Taken before administrative deletion. Kept 90 days. */
  PRE_DELETE
}
