/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a workspace. Sizing is optional and defaults apply when absent.
 *
 * @param name the workspace name, unique per owner
 * @param memoryMib guest memory in MiB, empty for the default
 * @param vcpuCount virtual CPU count, empty for the default
 */
public record CreateWorkspaceRequest(
    @NotBlank @Size(max = 64) String name,
    @Min(1024) @Max(4096) Integer memoryMib,
    @Min(1) @Max(4) Integer vcpuCount) {}
