/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** Demo identity must never be published to a non-loopback caller. */
final class DemoLoopbackTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void demoIdentityIsPublishedOnlyOnLoopbackRequests() throws Exception {
        BffAuthenticationFilter filter =
                new BffAuthenticationFilter(
                        mock(DevConnectService.class),
                        mock(JwtService.class),
                        mock(UserService.class),
                        true);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        filter.doFilter(local, new MockHttpServletResponse(), chain);
        assertTrue(SecurityContextHolder.getContext().getAuthentication() != null);

        SecurityContextHolder.clearContext();
        MockHttpServletRequest remote = new MockHttpServletRequest();
        remote.setRemoteAddr("203.0.113.5");
        filter.doFilter(remote, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
