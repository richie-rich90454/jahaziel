/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Canonical basis and Microsoft secret names must never survive redaction. */
class SecretRedactorCanonicalTest {
    @Test
    void redactsCanonicalCredentialsAndWebhookState() {
        String raw =
                "BASIS_AUTH_CLIENT_SECRET=s3cr3t\n"
                        + "BASIS_AUTH_RESOURCE_SECRET=res-sec\n"
                        + "BASIS_AUTH_COOKIE_SECRET=cookie-sec\n"
                        + "MICROSOFT_CLIENT_SECRET=ms-sec\n"
                        + "MICROSOFT_WEBHOOK_STATE=wh-state\n"
                        + "BASIS_AUTH_ISSUER=https://idp.example.com/authorize\n";

        String safe = SecretRedactor.redact(raw);

        assertFalse(safe.contains("s3cr3t"));
        assertFalse(safe.contains("res-sec"));
        assertFalse(safe.contains("cookie-sec"));
        assertFalse(safe.contains("ms-sec"));
        assertFalse(safe.contains("wh-state"));
        assertFalse(safe.contains("https://idp.example.com/authorize"));
        assertTrue(safe.contains("[redacted]"));
    }

    @Test
    void ordinaryQueryStateIsStillVisible() {
        String safe = SecretRedactor.redact("GET /cb?code=one&state=ok");
        assertTrue(safe.contains("state=ok"));
    }
}
