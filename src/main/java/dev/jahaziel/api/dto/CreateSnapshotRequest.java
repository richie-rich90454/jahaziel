/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to create a manual snapshot.
 *
 * @param tag the human tag, unique per workspace
 */
public final class CreateSnapshotRequest {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[a-zA-Z0-9-_]+$")
    private final String tag;

    public CreateSnapshotRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-zA-Z0-9-_]+$") String tag) {
        this.tag = tag;
    }

    public String tag() {
        return this.tag;
    }

    public String getTag() {
        return this.tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateSnapshotRequest)) return false;
        CreateSnapshotRequest other = (CreateSnapshotRequest) o;
        return (java.util.Objects.equals(this.tag, other.tag));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.tag);
    }

    @Override
    public String toString() {
        return "CreateSnapshotRequest[" + "tag=" + this.tag + "]";
    }
}
