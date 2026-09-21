/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import java.util.Map;

/**
 * Error body. Never carries stack traces; the correlation id links to the server log.
 *
 * @param code the machine-readable code
 * @param message the human message
 * @param correlationId the log correlation id
 * @param details extra detail pairs
 */
public record ApiError(
    String code, String message, String correlationId, Map<String, String> details) {}
