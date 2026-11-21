/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The registered header filter covers HTML pages without weakening the API-facing CSP. */
class SecurityHeadersFilterTest {
    @Test
    void htmlResponsesGetDefaultsAndInlineScriptCsp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/ui");
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new SecurityHeaders.HtmlHeaderFilter().doFilter(request, response, chain);

        String csp = response.getHeader(SecurityHeaders.CONTENT_SECURITY_POLICY);
        assertNotNull(csp);
        assertTrue(csp.contains("script-src 'self' 'unsafe-inline'"));
        assertEquals(
                SecurityHeaders.NOSNIFF,
                response.getHeader(SecurityHeaders.X_CONTENT_TYPE_OPTIONS));
        assertEquals(SecurityHeaders.DENY, response.getHeader(SecurityHeaders.X_FRAME_OPTIONS));
        assertNotNull(chain.getRequest());
    }

    @Test
    void apiResponsesAreLeftAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityHeaders.HtmlHeaderFilter().doFilter(request, response, new MockFilterChain());

        assertNull(response.getHeader(SecurityHeaders.CONTENT_SECURITY_POLICY));
        assertNull(response.getHeader(SecurityHeaders.X_CONTENT_TYPE_OPTIONS));
    }

    @Test
    void adminPageIsRecognizedWithoutAnAcceptHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/_admin/diagnostics");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityHeaders.HtmlHeaderFilter().doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(SecurityHeaders.CONTENT_SECURITY_POLICY));
    }
}
