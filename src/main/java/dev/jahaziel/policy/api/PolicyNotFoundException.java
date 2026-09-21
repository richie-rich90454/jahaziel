/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.api;

import java.util.UUID;

/** Thrown when a policy does not exist. */
public final class PolicyNotFoundException extends RuntimeException {
  private final UUID policyId;

  /**
   * Creates the exception.
   *
   * @param policyId the missing policy identifier
   */
  public PolicyNotFoundException(UUID policyId) {
    super("Policy " + policyId + " not found");
    this.policyId = policyId;
  }

  /** Returns the missing policy identifier. */
  public UUID policyId() {
    return policyId;
  }
}
