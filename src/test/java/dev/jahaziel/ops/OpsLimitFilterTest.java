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

/** Decisions made by {@link OpsLimitFilter} at the API boundary. */
class OpsLimitFilterTest {
    private static final int RATE_LIMIT = 1;
    private static final long WINDOW_MILLIS = 60000L;
    private static final long SESSION_LIMIT = 1L;

    private OpsLimitFilter filter(QuotaService quotas, RateLimiter limiter) {
        return new OpsLimitFilter(quotas, limiter, RATE_LIMIT, WINDOW_MILLIS, SESSION_LIMIT);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void rateLimitReturns429ProblemJson() throws Exception {
        OpsLimitFilter filter =
                filter(null, new RateLimiter(new TestClock("2026-01-01T00:00:00Z")));
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/workspaces"), first, new MockFilterChain());
        assertEquals(200, first.getStatus());

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/workspaces"), second, new MockFilterChain());
        assertEquals(429, second.getStatus());
        assertTrue(second.getContentType().startsWith("application/problem+json"));
        assertTrue(second.getContentAsString().contains("\"status\":429"));
        assertTrue(second.getContentAsString().contains("Too Many Requests"));
        assertTrue(second.getContentAsString().contains("rate limit exceeded"));
    }

    @Test
    void quotaReturns403ProblemJson() throws Exception {
        QuotaService quotas = new QuotaService();
        quotas.reserve("user", "ip:127.0.0.1", QuotaService.SESSION, 1L, SESSION_LIMIT);
        OpsLimitFilter filter = filter(quotas, null);

        MockHttpServletResponse reply = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/workspaces"), reply, new MockFilterChain());
        assertEquals(403, reply.getStatus());
        assertTrue(reply.getContentType().startsWith("application/problem+json"));
        assertTrue(reply.getContentAsString().contains("\"status\":403"));
        assertTrue(reply.getContentAsString().contains("quota exceeded"));
    }

    @Test
    void absentServicesFailOpen() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse reply = new MockHttpServletResponse();
        filter(null, null).doFilter(request("/api/v1/workspaces"), reply, chain);
        assertEquals(200, reply.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void healthAndNonApiPathsAreNeverLimited() throws Exception {
        RateLimiter limiter = new RateLimiter(new TestClock("2026-01-01T00:00:00Z"));
        limiter.tryAcquire("ip:127.0.0.1", RATE_LIMIT, java.time.Duration.ofMillis(WINDOW_MILLIS));
        OpsLimitFilter filter = filter(null, limiter);

        MockFilterChain healthChain = new MockFilterChain();
        MockHttpServletResponse health = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/health"), health, healthChain);
        assertEquals(200, health.getStatus());
        assertNotNull(healthChain.getRequest());

        MockFilterChain otherChain = new MockFilterChain();
        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(request("/health"), other, otherChain);
        assertEquals(200, other.getStatus());
        assertNotNull(otherChain.getRequest());
    }

    @Test
    void quotaSlotIsReleasedAfterEachRequest() throws Exception {
        QuotaService quotas = new QuotaService();
        OpsLimitFilter filter = filter(quotas, null);
        for (int index = 0; index < 2; index++) {
            MockHttpServletResponse reply = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/workspaces"), reply, new MockFilterChain());
            assertEquals(200, reply.getStatus());
        }
        assertEquals(0L, quotas.usage("user", "ip:127.0.0.1", QuotaService.SESSION));
    }
}
