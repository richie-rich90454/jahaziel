/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Current user for reads.
 *
 * @param userId the user identifier
 * @param email the email claim
 * @param displayName the display name claim
 * @param roles role names
 * @param mfaEnabled whether MFA enrollment is completed
 */
public final class UserDto {

    private final UUID userId;

    private final String email;

    private final String displayName;

    private final List<String> roles;

    private final boolean mfaEnabled;

    public UserDto(
            UUID userId, String email, String displayName, List<String> roles, boolean mfaEnabled) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.roles = roles;
        this.mfaEnabled = mfaEnabled;
    }

    public UUID userId() {
        return this.userId;
    }

    public UUID getUserId() {
        return this.userId;
    }

    public String email() {
        return this.email;
    }

    public String getEmail() {
        return this.email;
    }

    public String displayName() {
        return this.displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public List<String> roles() {
        return this.roles;
    }

    public List<String> getRoles() {
        return this.roles;
    }

    public boolean mfaEnabled() {
        return this.mfaEnabled;
    }

    public boolean getMfaEnabled() {
        return this.mfaEnabled;
    }

    public boolean isMfaEnabled() {
        return this.mfaEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserDto)) return false;
        UserDto other = (UserDto) o;
        return (java.util.Objects.equals(this.userId, other.userId))
                && java.util.Objects.equals(this.email, other.email)
                && java.util.Objects.equals(this.displayName, other.displayName)
                && java.util.Objects.equals(this.roles, other.roles)
                && this.mfaEnabled == other.mfaEnabled;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.userId, this.email, this.displayName, this.roles, this.mfaEnabled);
    }

    @Override
    public String toString() {
        return "UserDto["
                + "userId="
                + this.userId
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
                + "mfaEnabled="
                + this.mfaEnabled
                + "]";
    }
}
