/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.local;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LocalTotpTest {
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238Sha1VectorsTruncatedToSixDigits() {
        assertEquals("287082", Totp.code(RFC_SECRET, 59L));
        assertEquals("081804", Totp.code(RFC_SECRET, 1111111109L));
        assertEquals("050471", Totp.code(RFC_SECRET, 1111111111L));
        assertEquals("005924", Totp.code(RFC_SECRET, 1234567890L));
        assertEquals("279037", Totp.code(RFC_SECRET, 2000000000L));
        assertEquals("353130", Totp.code(RFC_SECRET, 20000000000L));
    }

    @Test
    void codesAreSixDigits() {
        byte[] secret = Totp.newSecret();
        String code = Totp.code(secret, 1700000000L);
        assertEquals(6, code.length());
        assertTrue(code.matches("[0-9]{6}"));
    }

    @Test
    void acceptsAdjacentStepsWithinWindow() {
        long now = 30L * 10L;
        String previous = Totp.code(RFC_SECRET, 30L * 9L);
        String next = Totp.code(RFC_SECRET, 30L * 11L);

        assertTrue(Totp.verify(RFC_SECRET, previous, now, -1L) >= 0);
        assertTrue(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, now), now, -1L) >= 0);
        assertTrue(Totp.verify(RFC_SECRET, next, now, -1L) >= 0);
    }

    @Test
    void rejectsCodesOutsideWindowAndWrongCodes() {
        long now = 30L * 10L;
        String tooOld = Totp.code(RFC_SECRET, 30L * 8L);
        String tooNew = Totp.code(RFC_SECRET, 30L * 12L);

        assertEquals(-1, Totp.verify(RFC_SECRET, tooOld, now, -1L));
        assertEquals(-1, Totp.verify(RFC_SECRET, tooNew, now, -1L));
        assertEquals(-1, Totp.verify(RFC_SECRET, "000000", now, -1L));
        assertEquals(-1, Totp.verify(RFC_SECRET, null, now, -1L));
    }

    @Test
    void rejectsReplayOfAnAlreadyUsedCounter() {
        long now = 30L * 10L;
        String current = Totp.code(RFC_SECRET, now);
        int counter = Totp.verify(RFC_SECRET, current, now, -1L);
        assertTrue(counter >= 0);

        assertEquals(-1, Totp.verify(RFC_SECRET, current, now, counter));
        assertEquals(-1, Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, 30L * 9L), now, counter));
    }

    @Test
    void newSecretHasExpectedLength() {
        assertEquals(20, Totp.newSecret().length);
    }
}
