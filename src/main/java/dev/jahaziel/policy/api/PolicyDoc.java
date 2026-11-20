/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable policy document. A null scope id means the policy applies globally.
 *
 * @param id the policy identifier
 * @param scope the policy scope, for example {@code workspace}
 * @param scopeId the scope target id, or null for global scope
 * @param policyJson the policy document as JSON
 * @param updatedAt the last update timestamp
 * @param updatedBy the last updater
 */
public final class PolicyDoc {
    private final UUID id;
    private final String scope;
    private final String scopeId;
    private final String policyJson;
    private final Instant updatedAt;
    private final String updatedBy;

    /**
     * Creates the document.
     *
     * @param id the policy identifier
     * @param scope the policy scope, for example {@code workspace}
     * @param scopeId the scope target id, or null for global scope
     * @param policyJson the policy document as JSON
     * @param updatedAt the last update timestamp
     * @param updatedBy the last updater
     * @throws NullPointerException when any non-nullable component is null
     * @throws IllegalArgumentException when scope, policyJson, or updatedBy is blank
     */
    public PolicyDoc(
            UUID id,
            String scope,
            String scopeId,
            String policyJson,
            Instant updatedAt,
            String updatedBy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        if (policyJson == null || policyJson.isBlank()) {
            throw new IllegalArgumentException("policyJson must not be blank");
        }
        if (updatedBy.isBlank()) {
            throw new IllegalArgumentException("updatedBy must not be blank");
        }
        this.id = id;
        this.scope = scope;
        this.scopeId = scopeId;
        this.policyJson = policyJson;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
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

    /** Returns the policy document as JSON. */
    public String policyJson() {
        return policyJson;
    }

    public String getPolicyJson() {
        return policyJson;
    }

    /** Returns the last update timestamp. */
    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Returns the last updater. */
    public String updatedBy() {
        return updatedBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PolicyDoc)) {
            return false;
        }
        PolicyDoc that = (PolicyDoc) other;
        return id.equals(that.id)
                && scope.equals(that.scope)
                && Objects.equals(scopeId, that.scopeId)
                && policyJson.equals(that.policyJson)
                && updatedAt.equals(that.updatedAt)
                && updatedBy.equals(that.updatedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, scope, scopeId, policyJson, updatedAt, updatedBy);
    }

    @Override
    public String toString() {
        return "PolicyDoc[id="
                + id
                + ", scope="
                + scope
                + ", scopeId="
                + scopeId
                + ", policyJson="
                + policyJson
                + ", updatedAt="
                + updatedAt
                + ", updatedBy="
                + updatedBy
                + "]";
    }
}
