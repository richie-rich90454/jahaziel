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
public record AdminActionRequest(@NotBlank String reason) {}
