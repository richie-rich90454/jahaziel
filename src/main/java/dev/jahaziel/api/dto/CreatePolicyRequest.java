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
public record CreatePolicyRequest(String scope, String scopeId, String policyJson) {}
