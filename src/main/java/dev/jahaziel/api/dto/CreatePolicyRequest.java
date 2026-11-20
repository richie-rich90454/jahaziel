/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

/**
 * Request to store a policy document.
 *
 * @param scope the policy scope, global, user, or workspace
 * @param scopeId the scope target id, null for global scope
 * @param policyJson the policy document
 */
public final class CreatePolicyRequest {

    private final String scope;

    private final String scopeId;

    private final String policyJson;

    public CreatePolicyRequest(String scope, String scopeId, String policyJson) {
        this.scope = scope;
        this.scopeId = scopeId;
        this.policyJson = policyJson;
    }

    public String scope() {
        return this.scope;
    }

    public String getScope() {
        return this.scope;
    }

    public String scopeId() {
        return this.scopeId;
    }

    public String getScopeId() {
        return this.scopeId;
    }

    public String policyJson() {
        return this.policyJson;
    }

    public String getPolicyJson() {
        return this.policyJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreatePolicyRequest)) return false;
        CreatePolicyRequest other = (CreatePolicyRequest) o;
        return (java.util.Objects.equals(this.scope, other.scope))
                && java.util.Objects.equals(this.scopeId, other.scopeId)
                && java.util.Objects.equals(this.policyJson, other.policyJson);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.scope, this.scopeId, this.policyJson);
    }

    @Override
    public String toString() {
        return "CreatePolicyRequest["
                + "scope="
                + this.scope
                + ", "
                + "scopeId="
                + this.scopeId
                + ", "
                + "policyJson="
                + this.policyJson
                + "]";
    }
}
