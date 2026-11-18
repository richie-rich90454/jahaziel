/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class SecurityHeadersTest {

    @Test
    void cspForbidsInlineScriptsAndEval() {
        String csp = SecurityHeaders.CSP;

        assertFalse(csp.contains("unsafe-inline"));
        assertFalse(csp.contains("unsafe-eval"));
        assertTrue(csp.contains("script-src 'self'"));
        assertTrue(csp.contains("object-src 'none'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
    }

    @Test
    void defaultsIncludeEveryMandatoryHeader() {
        HashMap<String, String> headers = SecurityHeaders.defaults();

        assertEquals(SecurityHeaders.CSP, headers.get(SecurityHeaders.CONTENT_SECURITY_POLICY));
        assertEquals(
                SecurityHeaders.NOSNIFF, headers.get(SecurityHeaders.X_CONTENT_TYPE_OPTIONS));
        assertEquals(
                SecurityHeaders.NO_REFERRER, headers.get(SecurityHeaders.REFERRER_POLICY));
        assertEquals(SecurityHeaders.HSTS, headers.get(SecurityHeaders.STRICT_TRANSPORT_SECURITY));
        assertEquals(SecurityHeaders.DENY, headers.get(SecurityHeaders.X_FRAME_OPTIONS));
    }

    @Test
    void cookieCarriesSecureAttributes() {
        HashMap<String, String> headers =
                new SecurityHeaders.Builder().cookie("session", "abc123").build();
        String cookie = headers.get(SecurityHeaders.SET_COOKIE);

        assertTrue(cookie.startsWith("session=abc123;"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Strict"));
    }
}
