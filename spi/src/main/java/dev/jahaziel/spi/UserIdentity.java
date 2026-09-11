/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.List;
import java.util.Objects;

/**
 * Identity of a user as seen by the platform.
 *
 * @param externalId the subject claim from the identity provider
 * @param email the email claim
 * @param displayName the display name claim
 * @param roles role names assigned to the user
 * @param groups group names the user belongs to
 */
public record UserIdentity(
    String externalId, String email, String displayName, List<String> roles, List<String> groups) {
  public UserIdentity {
    Objects.requireNonNull(externalId, "externalId");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(roles, "roles");
    Objects.requireNonNull(groups, "groups");
    roles = List.copyOf(roles);
    groups = List.copyOf(groups);
  }
}
