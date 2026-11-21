/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.MfaGate;
import dev.jahaziel.identity.domain.UserId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.UUID;

/** The shared administrator gate requires MFA for every API controller that uses it. */
final class ApiControllerBaseMfaTest {
    @AfterEach
    void clearHook() {
        MfaGate.install(null);
    }

    @Test
    void requireAdminRejectsAnAdminWithoutMfa() {
        UUID id = UUID.randomUUID();
        UserId user = new UserId(id);
        AuthorizationService auth = mock(AuthorizationService.class);
        final boolean[] enabled = {false};
        when(auth.isAdmin(user)).thenReturn(true);
        MfaGate.install(
                new MfaGate() {
                    @Override
                    public boolean enabled(UserId checked) {
                        return enabled[0];
                    }
                });
        Probe probe = new Probe(auth);

        assertThrows(ApiFailureException.class, () -> probe.check(principal(id)));
        enabled[0] = true;
        assertEquals(user, probe.check(principal(id)));
    }

    private static Principal principal(UUID id) {
        return () -> id.toString();
    }

    private static final class Probe extends ApiControllerBase {
        private Probe(AuthorizationService auth) {
            super(auth);
        }

        private UserId check(Principal principal) {
            return requireAdmin(principal);
        }
    }
}
