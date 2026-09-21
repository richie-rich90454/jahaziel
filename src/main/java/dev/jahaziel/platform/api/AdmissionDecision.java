/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.api;

/** Admission decision for a workspace start. Memory is reserved, never overcommitted. */
public sealed interface AdmissionDecision {
  /**
   * Start approved.
   *
   * @param memoryMib the approved memory, possibly the soft floor
   */
  record Admit(long memoryMib) implements AdmissionDecision {}

  /**
   * Start refused.
   *
   * @param reason why the start is refused
   */
  record Refuse(String reason) implements AdmissionDecision {}
}
