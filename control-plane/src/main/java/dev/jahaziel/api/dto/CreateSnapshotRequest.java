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
public record CreateSnapshotRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-zA-Z0-9-_]+$") String tag) {}
