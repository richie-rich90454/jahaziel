/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.local;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

final class LocalPasswordHasherTest {
    @Test
    void hashIsDeterministicAndVerifies() {
        byte[] salt = PasswordHasher.newSalt();
        char[] password = "correct horse battery staple".toCharArray();

        byte[] first = PasswordHasher.hash(password, salt, 1000);
        byte[] second = PasswordHasher.hash(password, salt, 1000);

        assertEquals(32, first.length);
        assertArrayEquals(first, second);
        assertTrue(PasswordHasher.verify(password, salt, 1000, first));
    }

    @Test
    void wrongPasswordIsRejected() {
        byte[] salt = PasswordHasher.newSalt();
        byte[] hash = PasswordHasher.hash("right-password".toCharArray(), salt, 1000);

        assertFalse(PasswordHasher.verify("wrong-password".toCharArray(), salt, 1000, hash));
    }

    @Test
    void differentSaltOrIterationsChangesHash() {
        byte[] salt = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] otherSalt = "fedcba9876543210".getBytes(StandardCharsets.US_ASCII);
        char[] password = "password".toCharArray();

        byte[] hash = PasswordHasher.hash(password, salt, 1000);
        assertFalse(PasswordHasher.verify(password, otherSalt, 1000, hash));
        assertFalse(PasswordHasher.verify(password, salt, 1001, hash));
    }

    @Test
    void newSaltIsRandom() {
        byte[] first = PasswordHasher.newSalt();
        byte[] second = PasswordHasher.newSalt();
        assertEquals(16, first.length);
        assertFalse(java.util.Arrays.equals(first, second));
    }
}
