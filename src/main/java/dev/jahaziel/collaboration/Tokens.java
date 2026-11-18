/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Generates random tokens and stores only their SHA-256 hex digest. */
public final class Tokens {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {}

    public static String generate(String prefix) {
        byte[] value = new byte[24];
        RANDOM.nextBytes(value);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static String hash(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token is required");
        }
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder(digest.length * 2);
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xFF;
                text.append(HEX[value >>> 4]).append(HEX[value & 0x0F]);
            }
            return text.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
