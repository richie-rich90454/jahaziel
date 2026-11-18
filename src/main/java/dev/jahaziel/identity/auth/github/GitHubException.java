/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.github;

/** Failure in the GitHub provider. Messages never contain tokens or secrets. */
public class GitHubException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GitHubException(String message) {
        super(message);
    }

    public GitHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
