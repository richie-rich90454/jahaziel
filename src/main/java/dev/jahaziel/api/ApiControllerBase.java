/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.security.CanonicalPathGuard;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * Shared authentication, workspace authorization and path-containment checks for API controllers.
 */
public abstract class ApiControllerBase {
    protected static final String READ = "read";
    protected static final String WRITE = "write";
    protected static final String ADMIN = "admin";

    protected final AuthorizationService auth;

    protected ApiControllerBase(AuthorizationService auth) {
        if (auth == null) {
            throw new IllegalArgumentException("auth is required");
        }
        this.auth = auth;
    }

    protected UserId requireUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw ApiFailureException.unauthorized();
        }
        try {
            return new UserId(UUID.fromString(principal.getName()));
        } catch (IllegalArgumentException invalid) {
            throw ApiFailureException.unauthorized();
        }
    }

    protected WorkspaceId requireWorkspace(Principal principal, UUID id, String permission) {
        UserId user = requireUser(principal);
        if (id == null) {
            throw ApiFailureException.badRequest("Workspace id is required");
        }
        return requireWorkspaceFor(user, new WorkspaceId(id), permission);
    }

    protected WorkspaceId requireWorkspaceFor(
            UserId user, WorkspaceId workspace, String permission) {
        if (workspace == null) {
            throw ApiFailureException.badRequest("Workspace id is required");
        }
        if (!auth.isAllowed(user, permission, workspace)) {
            throw ApiFailureException.forbidden();
        }
        return workspace;
    }

    protected UserId requireAdmin(Principal principal) {
        UserId user = requireUser(principal);
        if (!auth.isAdmin(user)) {
            throw ApiFailureException.forbidden();
        }
        return user;
    }

    /**
     * Applies the realpath containment guard to a caller-supplied workspace-relative path. A false
     * result from the guard is always a client error, never a server detail.
     */
    protected void requireContained(Path root, String relative) {
        if (relative == null || relative.isEmpty()) {
            throw ApiFailureException.badRequest("A relative path is required");
        }
        Path candidate;
        try {
            candidate = Paths.get(relative);
        } catch (RuntimeException invalid) {
            throw ApiFailureException.badRequest("The relative path is invalid");
        }
        if (!CanonicalPathGuard.check(root, candidate)) {
            throw ApiFailureException.badRequest("The path escapes its workspace root");
        }
    }

    /** Parses an ISO-8601 instant without exposing parser details to the caller. */
    protected Instant parseInstant(String value) {
        if (value == null || value.isEmpty()) {
            throw ApiFailureException.badRequest("A timestamp is required");
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException invalid) {
            throw ApiFailureException.badRequest("A timestamp is invalid");
        }
    }

    /** Builds the home root that data-tool services resolve relative paths against. */
    protected Path workspaceHome(String storageRoot, UUID workspaceId) {
        return Paths.get(storageRoot, "workspaces", workspaceId.toString(), "home");
    }
}
