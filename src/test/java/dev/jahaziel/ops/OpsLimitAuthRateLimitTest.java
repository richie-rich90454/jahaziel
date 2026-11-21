/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

/** Strict rate limiting on the authentication surface and exact health-path matching. */
class OpsLimitAuthRateLimitTest {
    private MockHttpServletRequest request(String path, String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(address);
        return request;
    }

    private OpsLimitFilter.AuthRateLimitFilter authFilter(RateLimiter limiter, int limit) {
        return new OpsLimitFilter.AuthRateLimitFilter(limiter, limit, 60000L);
    }

    @Test
    void loginIsLimitedByIpAndSubmittedIdentity() throws Exception {
        RateLimiter limiter = new RateLimiter(new TestClock("2026-01-01T00:00:00Z"));
        OpsLimitFilter.AuthRateLimitFilter filter = authFilter(limiter, 1);

        MockHttpServletRequest first = request("/api/login", "127.0.0.1");
        first.setParameter("username", "alice");
        assertEquals(200, firstReplyStatus(filter, first));

        MockHttpServletRequest second = request("/api/login", "127.0.0.1");
        second.setParameter("username", "alice");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(second, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentType().startsWith("application/problem+json"));

        MockHttpServletRequest other = request("/api/login", "127.0.0.1");
        other.setParameter("username", "bob");
        assertEquals(200, firstReplyStatus(filter, other));
    }

    private int firstReplyStatus(
            OpsLimitFilter.AuthRateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse reply = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, reply, chain);
        assertNotNull(chain.getRequest());
        return reply.getStatus();
    }

    @Test
    void everyAuthEndpointIsGuarded() throws Exception {
        String[] paths =
                new String[] {
                    "/api/auth/token",
                    "/api/auth/basis/callback",
                    "/api/auth/basis/backchannel",
                    "/api/login"
                };
        for (int i = 0; i < paths.length; i++) {
            RateLimiter limiter = new RateLimiter(new TestClock("2026-01-01T00:00:00Z"));
            OpsLimitFilter.AuthRateLimitFilter filter = authFilter(limiter, 1);
            String address = "10.0.0." + (i + 1);
            assertEquals(200, firstReplyStatus(filter, request(paths[i], address)));

            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilter(request(paths[i], address), blocked, new MockFilterChain());
            assertEquals(429, blocked.getStatus(), paths[i]);
        }
    }

    @Test
    void nonAuthPathsPassThroughTheAuthFilter() throws Exception {
        OpsLimitFilter.AuthRateLimitFilter filter =
                authFilter(new RateLimiter(new TestClock("2026-01-01T00:00:00Z")), 1);
        assertEquals(200, firstReplyStatus(filter, request("/api/v1/workspaces", "127.0.0.1")));
    }

    @Test
    void healthBypassMatchesExactPathsOnly() throws Exception {
        RateLimiter limiter = new RateLimiter(new TestClock("2026-01-01T00:00:00Z"));
        limiter.tryAcquire("ip:127.0.0.1", 1, Duration.ofMillis(60000L));
        OpsLimitFilter filter = new OpsLimitFilter(null, limiter, 1, 60000L, 1L);

        MockHttpServletResponse reply = new MockHttpServletResponse();
        filter.doFilter(
                request("/api/v1/workspaces/health-check", "127.0.0.1"),
                reply,
                new MockFilterChain());
        assertEquals(429, reply.getStatus());
    }
}
