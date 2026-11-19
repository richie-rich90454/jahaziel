/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import dev.jahaziel.identity.auth.basis.BasisResourceFilter;
import dev.jahaziel.identity.auth.basis.BasisScopeGuard;
import dev.jahaziel.identity.auth.basis.TokenVerifier;
import dev.jahaziel.identity.auth.wiring.UserIdentityStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

/** BasisResourceFilter publishes a local user id only after it has verified the access token. */
final class BasisPrincipalFilterTest {
    private static final String PATH = "/api/v1/workspaces/123/files";

    private TokenVerifier verifier;
    private TokenVerifier.VerifiedToken verified;
    private UserIdentityStore identities;
    private BasisResourceFilter filter;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .type(new JOSEObjectType("at+jwt"))
                        .build();
        SignedJWT signed =
                new SignedJWT(header, new JWTClaimsSet.Builder().subject("sub-1").build());
        signed.sign(new MACSigner("filter-test-signing-secret-1234567890"));
        token = signed.serialize();
        verifier = mock(TokenVerifier.class);
        verified = mock(TokenVerifier.VerifiedToken.class);
        when(verified.claims()).thenReturn(new JWTClaimsSet.Builder().subject("sub-1").build());
        when(verifier.verifyAccessToken(token)).thenReturn(verified);
        identities = mock(UserIdentityStore.class);
        BasisResourceFilter.setIdentityStore(identities);
        filter =
                new BasisResourceFilter(
                        verifier, mock(BasisScopeGuard.class), new String[0], new String[0]);
    }

    @AfterEach
    void tearDown() {
        BasisResourceFilter.setIdentityStore(null);
    }

    @Test
    void verifiedTokenResolvesAndPublishesTheLinkedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identities.findUserId("basis-auth", "sub-1")).thenReturn(userId);
        MockHttpServletRequest request = request();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(
                userId.toString(), request.getAttribute(BasisResourceFilter.PRINCIPAL_ATTRIBUTE));
        assertNotNull(chain.getRequest());
        verify(identities).findUserId("basis-auth", "sub-1");
    }

    @Test
    void anUnlinkedSubjectDoesNotPublishAPrincipal() throws Exception {
        when(identities.findUserId("basis-auth", "sub-1")).thenReturn(null);
        MockHttpServletRequest request = request();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(request.getAttribute(BasisResourceFilter.PRINCIPAL_ATTRIBUTE));
    }

    @Test
    void aRejectedTokenNeverPublishesAPrincipal() throws Exception {
        when(verifier.verifyAccessToken(token))
                .thenThrow(new TokenVerifier.TokenRejectedException("bad token"));
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(request.getAttribute(BasisResourceFilter.PRINCIPAL_ATTRIBUTE));
        assertNull(chain.getRequest());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
