/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.local;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2 password hashing with a random salt and a constant-time comparison. */
public final class PasswordHasher {
    public static final int ITERATIONS = 120000;

    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private PasswordHasher() {
    }

    /** Returns a fresh 128-bit salt. */
    public static byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /** Derives a 256-bit hash from a password and salt. */
    public static byte[] hash(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException failure) {
            throw new LocalAuthException("password hashing failed", failure);
        } finally {
            spec.clearPassword();
        }
    }

    /** Verifies a password against a stored hash in constant time. */
    public static boolean verify(char[] password, byte[] salt, int iterations, byte[] expected) {
        byte[] actual = hash(password, salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }
}
