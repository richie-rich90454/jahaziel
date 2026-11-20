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
public final class ApiError {

    private final String code;

    private final String message;

    private final String correlationId;

    private final Map<String, String> details;

    public ApiError(
            String code, String message, String correlationId, Map<String, String> details) {
        this.code = code;
        this.message = message;
        this.correlationId = correlationId;
        this.details = details;
    }

    public String code() {
        return this.code;
    }

    public String getCode() {
        return this.code;
    }

    public String message() {
        return this.message;
    }

    public String getMessage() {
        return this.message;
    }

    public String correlationId() {
        return this.correlationId;
    }

    public String getCorrelationId() {
        return this.correlationId;
    }

    public Map<String, String> details() {
        return this.details;
    }

    public Map<String, String> getDetails() {
        return this.details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiError)) return false;
        ApiError other = (ApiError) o;
        return (java.util.Objects.equals(this.code, other.code))
                && java.util.Objects.equals(this.message, other.message)
                && java.util.Objects.equals(this.correlationId, other.correlationId)
                && java.util.Objects.equals(this.details, other.details);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.code, this.message, this.correlationId, this.details);
    }

    @Override
    public String toString() {
        return "ApiError["
                + "code="
                + this.code
                + ", "
                + "message="
                + this.message
                + ", "
                + "correlationId="
                + this.correlationId
                + ", "
                + "details="
                + this.details
                + "]";
    }
}
