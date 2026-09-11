/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Current user for reads.
 *
 * @param userId the user identifier
 * @param email the email claim
 * @param displayName the display name claim
 * @param roles role names
 * @param mfaEnabled whether MFA enrollment is completed
 */
public record UserDto(
    UUID userId, String email, String displayName, List<String> roles, boolean mfaEnabled) {}
