/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Who performed an audited action. */
public enum ActorType {
  /** A regular user. */
  USER,
  /** The platform itself. */
  SYSTEM,
  /** A node agent. */
  NODE,
  /** A human administrator. */
  ADMIN
}
