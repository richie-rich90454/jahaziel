/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/** Explicit secret-name denylist and atomic whole-file writes. */
class OpsFilesSecretTest {
    @TempDir Path temp;

    @Test
    void canonicalCredentialNamesAreDenylisted() {
        assertTrue(OpsFiles.isSecretKey("BASIS_AUTH_CLIENT_SECRET"));
        assertTrue(OpsFiles.isSecretKey("BASIS_AUTH_RESOURCE_SECRET"));
        assertTrue(OpsFiles.isSecretKey("BASIS_AUTH_COOKIE_SECRET"));
        assertTrue(OpsFiles.isSecretKey("MICROSOFT_WEBHOOK_STATE"));
        assertTrue(OpsFiles.isSecretKey("CURRENT_URL_ORIGIN"));
        assertFalse(OpsFiles.isSecretKey("host"));
    }

    @Test
    void sanitizeTextMasksDenylistedNamesAndAtomicWriteReplacesContent() {
        assertTrue(OpsFiles.sanitizeText("MICROSOFT_WEBHOOK_STATE=abc123").contains("[REDACTED]"));
        assertFalse(OpsFiles.sanitizeText("MICROSOFT_WEBHOOK_STATE=abc123").contains("abc123"));

        Path file = temp.resolve("nested/state.psv");
        OpsFiles.writeTextAtomic(file, "first");
        OpsFiles.writeTextAtomic(file, "second");
        assertEquals("second", OpsFiles.readText(file));
    }
}
