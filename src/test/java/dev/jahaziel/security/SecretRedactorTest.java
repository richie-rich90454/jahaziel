/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecretRedactorTest {

    @Test
    void redactsTokensCodesSecretsVerifiersAndCookies() {
        String raw =
                "token=abc123 secret=def456 code=ghi789 verifier=jkl012\n"
                        + "Authorization: Bearer mno345\n"
                        + "Cookie: session=pqr678; theme=dark\n"
                        + "GET /cb?code=stu901&state=ok\n";

        String safe = SecretRedactor.redact(raw);

        assertFalse(safe.contains("abc123"));
        assertFalse(safe.contains("def456"));
        assertFalse(safe.contains("ghi789"));
        assertFalse(safe.contains("jkl012"));
        assertFalse(safe.contains("mno345"));
        assertFalse(safe.contains("pqr678"));
        assertFalse(safe.contains("stu901"));
        assertTrue(safe.contains("[redacted]"));
        assertTrue(safe.contains("state=ok"));
    }

    @Test
    void redactsJwtAndAppliesToToasts() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signaturevalue";

        String safe = SecretRedactor.safeForToast("jwt=" + jwt);

        assertFalse(safe.contains("eyJhbGciOiJIUzI1NiJ9"));
        assertFalse(safe.contains("signaturevalue"));
    }
}
