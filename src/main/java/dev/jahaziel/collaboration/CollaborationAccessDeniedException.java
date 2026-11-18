/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** Thrown when an actor lacks the workspace role required by a collaboration operation. */
public final class CollaborationAccessDeniedException extends RuntimeException {
    public CollaborationAccessDeniedException(String message) {
        super(message);
    }
}
