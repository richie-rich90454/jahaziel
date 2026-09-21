/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Decides whether a user may act on a resource. */
@JahazielSpi(version = 1)
public interface AuthorizationEngine {
  /**
   * Checks a permission.
   *
   * @param user the acting user
   * @param permission the required permission
   * @param resource the targeted resource
   * @return true when the action is allowed
   */
  boolean isAllowed(UserIdentity user, Permission permission, Resource resource);

  /**
   * Returns the effective policy for a user on a resource.
   *
   * @param user the acting user
   * @param resource the targeted resource
   * @return the effective policy
   */
  Policy effectivePolicy(UserIdentity user, Resource resource);
}
