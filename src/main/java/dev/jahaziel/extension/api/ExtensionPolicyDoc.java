/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable extension policy. A null scope id means the policy applies globally; a null version
 * constraint means versions are unrestricted.
 */
public final class ExtensionPolicyDoc {
    private final UUID id;
    private final String scope;
    private final String scopeId;
    private final String extensionId;
    private final String action;
    private final String versionConstraint;
    private final Instant updatedAt;

    /**
     * Creates the document.
     *
     * @param id the policy identifier
     * @param scope the policy scope, for example {@code workspace}
     * @param scopeId the scope target id, or null for global scope
     * @param extensionId the extension identifier
     * @param action the enforcement action, for example {@code allow}
     * @param versionConstraint the version constraint, or null when unrestricted
     * @param updatedAt the last update timestamp
     * @throws NullPointerException when any non-nullable component is null
     * @throws IllegalArgumentException when scope, extensionId, or action is blank
     */
    public ExtensionPolicyDoc(
            UUID id,
            String scope,
            String scopeId,
            String extensionId,
            String action,
            String versionConstraint,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        if (extensionId == null || extensionId.isBlank()) {
            throw new IllegalArgumentException("extensionId must not be blank");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        this.scope = scope;
        this.scopeId = scopeId;
        this.extensionId = extensionId;
        this.action = action;
        this.versionConstraint = versionConstraint;
    }

    /** Returns the policy identifier. */
    public UUID id() {
        return id;
    }

    public UUID getId() {
        return id;
    }

    /** Returns the policy scope. */
    public String scope() {
        return scope;
    }

    public String getScope() {
        return scope;
    }

    /** Returns the scope target id, or null for global scope. */
    public String scopeId() {
        return scopeId;
    }

    public String getScopeId() {
        return scopeId;
    }

    /** Returns the extension identifier. */
    public String extensionId() {
        return extensionId;
    }

    public String getExtensionId() {
        return extensionId;
    }

    /** Returns the enforcement action. */
    public String action() {
        return action;
    }

    public String getAction() {
        return action;
    }

    /** Returns the version constraint, or null when unrestricted. */
    public String versionConstraint() {
        return versionConstraint;
    }

    public String getVersionConstraint() {
        return versionConstraint;
    }

    /** Returns the last update timestamp. */
    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Compares by class and every component. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExtensionPolicyDoc other = (ExtensionPolicyDoc) o;
        return Objects.equals(id, other.id)
                && Objects.equals(scope, other.scope)
                && Objects.equals(scopeId, other.scopeId)
                && Objects.equals(extensionId, other.extensionId)
                && Objects.equals(action, other.action)
                && Objects.equals(versionConstraint, other.versionConstraint)
                && Objects.equals(updatedAt, other.updatedAt);
    }

    /** Returns a hash derived from every component. */
    @Override
    public int hashCode() {
        return Objects.hash(id, scope, scopeId, extensionId, action, versionConstraint, updatedAt);
    }

    /** Returns a readable representation. */
    @Override
    public String toString() {
        return "ExtensionPolicyDoc[id="
                + id
                + ", scope="
                + scope
                + ", scopeId="
                + scopeId
                + ", extensionId="
                + extensionId
                + ", action="
                + action
                + ", versionConstraint="
                + versionConstraint
                + ", updatedAt="
                + updatedAt
                + "]";
    }
}
