/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent;

/** Signals that an agent request was rejected by validation or containment policy. */
public final class AgentException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
