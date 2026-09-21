/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;
import java.util.List;
import java.util.Objects;

/**
 * Full user read model, including the identifier and MFA flag the plugin identity omits.
 *
 * @param id the user identifier
 * @param externalId the subject claim from the identity provider
 * @param email the email claim
 * @param displayName the display name claim
 * @param roles role names assigned to the user
 * @param groups group names the user belongs to
 * @param mfaEnabled whether MFA enrollment is completed
 */
public record AppUser(
    UserId id,
    String externalId,
    String email,
    String displayName,
    List<String> roles,
    List<String> groups,
    boolean mfaEnabled) {
  public AppUser {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(externalId, "externalId");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(roles, "roles");
    Objects.requireNonNull(groups, "groups");
    roles = List.copyOf(roles);
    groups = List.copyOf(groups);
  }
}
