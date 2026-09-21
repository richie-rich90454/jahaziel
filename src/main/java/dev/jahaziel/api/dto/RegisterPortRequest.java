/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A port to register for a workspace.
 *
 * @param port the port the workspace listens on
 * @param protocol one of http, https, tcp
 */
public record RegisterPortRequest(
    @NotNull @Min(1) @Max(65535) Integer port, @NotBlank String protocol) {}
