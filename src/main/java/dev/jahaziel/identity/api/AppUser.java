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
public final class AppUser {
    private final UserId id;
    private final String externalId;
    private final String email;
    private final String displayName;
    private final List<String> roles;
    private final List<String> groups;
    private final boolean mfaEnabled;

    /** Creates a full user read model. */
    public AppUser(
            UserId id,
            String externalId,
            String email,
            String displayName,
            List<String> roles,
            List<String> groups,
            boolean mfaEnabled) {
        this.id = Objects.requireNonNull(id, "id");
        this.externalId = Objects.requireNonNull(externalId, "externalId");
        this.email = Objects.requireNonNull(email, "email");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        this.groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
        this.mfaEnabled = mfaEnabled;
    }

    /** Returns the user identifier. */
    public UserId id() {
        return id;
    }

    public UserId getId() {
        return id;
    }

    /** Returns the identity provider subject. */
    public String externalId() {
        return externalId;
    }

    public String getExternalId() {
        return externalId;
    }

    /** Returns the email claim. */
    public String email() {
        return email;
    }

    public String getEmail() {
        return email;
    }

    /** Returns the display name claim. */
    public String displayName() {
        return displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Returns the role names. */
    public List<String> roles() {
        return roles;
    }

    public List<String> getRoles() {
        return roles;
    }

    /** Returns the group names. */
    public List<String> groups() {
        return groups;
    }

    public List<String> getGroups() {
        return groups;
    }

    /** Returns whether MFA enrollment is completed. */
    public boolean mfaEnabled() {
        return mfaEnabled;
    }

    public boolean getMfaEnabled() {
        return mfaEnabled;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    /** Compares by class and all fields. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AppUser that = (AppUser) o;
        return mfaEnabled == that.mfaEnabled
                && id.equals(that.id)
                && externalId.equals(that.externalId)
                && email.equals(that.email)
                && displayName.equals(that.displayName)
                && roles.equals(that.roles)
                && groups.equals(that.groups);
    }

    /** Returns the hash code derived from all fields. */
    @Override
    public int hashCode() {
        return Objects.hash(id, externalId, email, displayName, roles, groups, mfaEnabled);
    }

    /** Returns a readable representation. */
    @Override
    public String toString() {
        return "AppUser[id="
                + id
                + ", externalId="
                + externalId
                + ", email="
                + email
                + ", displayName="
                + displayName
                + ", roles="
                + roles
                + ", groups="
                + groups
                + ", mfaEnabled="
                + mfaEnabled
                + "]";
    }
}
