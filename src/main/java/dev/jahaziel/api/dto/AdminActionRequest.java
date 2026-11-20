/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request carrying an admin reason.
 *
 * @param reason why the action happens
 */
public final class AdminActionRequest {

    @NotBlank private final String reason;

    public AdminActionRequest(@NotBlank String reason) {
        this.reason = reason;
    }

    public String reason() {
        return this.reason;
    }

    public String getReason() {
        return this.reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminActionRequest)) return false;
        AdminActionRequest other = (AdminActionRequest) o;
        return (java.util.Objects.equals(this.reason, other.reason));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.reason);
    }

    @Override
    public String toString() {
        return "AdminActionRequest[" + "reason=" + this.reason + "]";
    }
}
