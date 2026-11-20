/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.systemconfig;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.security.Principal;

/** Shared fakes for the system-configuration tests. */
final class ConfigFixtures {
    static final String USER = "33333333-3333-3333-3333-333333333333";

    private ConfigFixtures() {}

    static Principal principal() {
        return new Principal() {
            @Override
            public String getName() {
                return USER;
            }
        };
    }

    static final class AllowAll extends AuthorizationService {
        @Override
        public boolean isAllowed(UserId user, String permission, WorkspaceId workspace) {
            return true;
        }

        @Override
        public boolean isAdmin(UserId user) {
            return true;
        }
    }

    static final class DenyAll extends AuthorizationService {
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
