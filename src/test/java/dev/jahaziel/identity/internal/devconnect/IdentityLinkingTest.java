/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.wiring.UserIdentityStore;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.BffSessionRepository;
import dev.jahaziel.identity.persistence.OidcPendingRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A second provider identity attaches only to the authenticated account, never by email. */
final class IdentityLinkingTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private AppUserRepository users;
    private UserIdentityStore identities;
    private DevConnectServiceImpl service;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        DevConnectProperties props = new DevConnectProperties();
        props.setAuthIssuer("https://idp.example.org");
        props.setRedirectUri("https://app.example/cb");
        props.setClientId("client");
        props.setClientSecret("secret");
        props.setResource("urn:example:api");
        users = mock(AppUserRepository.class);
        identities = mock(UserIdentityStore.class);
        currentUserId = UUID.randomUUID();
        when(users.findById(currentUserId)).thenReturn(Optional.of(active(currentUserId)));
        service =
                new DevConnectServiceImpl(
                        props,
                        mock(DevConnectClient.class),
                        mock(OidcPendingRepository.class),
                        mock(BffSessionRepository.class),
                        users,
                        mock(UserRoleRepository.class),
                        mock(JwtService.class),
                        CLOCK,
                        null,
                        identities);
    }

    @Test
    void anEmailMatchAloneNeverLinksToAnotherAccount() {
        UUID emailMatchUserId = UUID.randomUUID();
        when(users.findByEmailIgnoreCase("user@example.org"))
                .thenReturn(java.util.Collections.singletonList(active(emailMatchUserId)));
        when(identities.findUserId("passkey", "credential-1")).thenReturn(null);

        service.linkCredentialIdentity(currentUserId, identity("passkey", "credential-1"));

        verify(identities)
                .linkToExistingUser(
                        eq("passkey"),
                        eq("credential-1"),
                        eq(currentUserId),
                        eq("user@example.org"),
                        eq("User"),
                        eq(CLOCK.instant()));
        verify(users, never()).findByEmailIgnoreCase(anyString());
        verify(users, never()).save(any(AppUserEntity.class));
    }

    @Test
    void anIdentityAlreadyOwnedBySomeoneElseIsRejected() {
        when(identities.findUserId("passkey", "credential-1")).thenReturn(UUID.randomUUID());

        assertThrows(
                LoginRejectedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.linkCredentialIdentity(
                                currentUserId, identity("passkey", "credential-1"));
                    }
                });
        verify(identities, never())
                .linkToExistingUser(
                        anyString(),
                        anyString(),
                        any(UUID.class),
                        anyString(),
                        anyString(),
                        any(Instant.class));
    }

    private static AppUserEntity active(UUID userId) {
        AppUserEntity user = new AppUserEntity();
        user.setUserId(userId);
        user.setEmail("user@example.org");
        user.setStatus("active");
        return user;
    }

    private static AuthIdentity identity(String provider, String subject) {
        Map<String, String> claims = new HashMap<String, String>();
        return new AuthIdentity(provider, subject, "user@example.org", "User", true, false, claims);
    }
}
