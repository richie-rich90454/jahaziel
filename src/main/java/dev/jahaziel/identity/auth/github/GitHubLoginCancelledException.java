/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.github;

/** The user denied the authorization request. Callers must not retry automatically. */
public final class GitHubLoginCancelledException extends GitHubException {
    private static final long serialVersionUID = 1L;

    public GitHubLoginCancelledException() {
        super("github login was cancelled");
    }
}
