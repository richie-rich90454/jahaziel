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
public final class UserIdentity {

    private final String externalId;

    private final String email;

    private final String displayName;

    private final List<String> roles;

    private final List<String> groups;

    public UserIdentity(
            String externalId,
            String email,
            String displayName,
            List<String> roles,
            List<String> groups) {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(groups, "groups");
        roles = List.copyOf(roles);
        groups = List.copyOf(groups);
        this.externalId = externalId;
        this.email = email;
        this.displayName = displayName;
        this.roles = roles;
        this.groups = groups;
    }

    public String externalId() {
        return this.externalId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String email() {
        return this.email;
    }

    public String getEmail() {
        return email;
    }

    public String displayName() {
        return this.displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> roles() {
        return this.roles;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> groups() {
        return this.groups;
    }

    public List<String> getGroups() {
        return groups;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserIdentity)) return false;
        UserIdentity other = (UserIdentity) o;
        return (java.util.Objects.equals(this.externalId, other.externalId))
                && java.util.Objects.equals(this.email, other.email)
                && java.util.Objects.equals(this.displayName, other.displayName)
                && java.util.Objects.equals(this.roles, other.roles)
                && java.util.Objects.equals(this.groups, other.groups);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.externalId, this.email, this.displayName, this.roles, this.groups);
    }

    @Override
    public String toString() {
        return "UserIdentity["
                + "externalId="
                + this.externalId
                + ", "
                + "email="
                + this.email
                + ", "
                + "displayName="
                + this.displayName
                + ", "
                + "roles="
                + this.roles
                + ", "
                + "groups="
                + this.groups
                + "]";
    }
}
