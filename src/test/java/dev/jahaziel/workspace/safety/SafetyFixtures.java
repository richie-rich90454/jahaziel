/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.UUID;

/** Shared fakes for the workspace-safety tests. */
public final class SafetyFixtures {
    static final String USER = "11111111-1111-1111-1111-111111111111";
    static final String WORKSPACE = "22222222-2222-2222-2222-222222222222";

    private SafetyFixtures() {}

    static WorkspaceId workspace() {
        return new WorkspaceId(UUID.fromString(WORKSPACE));
    }

    static UserId user() {
        return new UserId(UUID.fromString(USER));
    }

    static Principal principal() {
        return new Principal() {
            @Override
            public String getName() {
                return USER;
            }
        };
    }

    static void write(Path file, String content) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static final class AllowAll extends AuthorizationService {
        @Override
        public boolean isAllowed(UserId user, String permission, WorkspaceId workspace) {
            return true;
        }

        @Override
        public boolean isAdmin(UserId user) {
            return true;
        }
    }

    public static final class DenyAll extends AuthorizationService {
        @Override
        public boolean isAllowed(UserId user, String permission, WorkspaceId workspace) {
            return false;
        }

        @Override
        public boolean isAdmin(UserId user) {
            return false;
        }
    }
}
