/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.api;

import java.util.UUID;

/** Thrown when an extension policy does not exist. */
public final class ExtensionPolicyNotFoundException extends RuntimeException {
  private final UUID policyId;

  /**
   * Creates the exception.
   *
   * @param policyId the missing policy identifier
   */
  public ExtensionPolicyNotFoundException(UUID policyId) {
    super("Extension policy " + policyId + " not found");
    this.policyId = policyId;
  }

  /** Returns the missing policy identifier. */
  public UUID policyId() {
    return policyId;
  }
}
