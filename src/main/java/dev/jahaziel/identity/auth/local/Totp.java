/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.local;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP: HMAC-SHA1, 30-second steps, six digits, plus/minus one step window. */
public final class Totp {
    public static final int STEP_SECONDS = 30;
    public static final int WINDOW = 1;

    private static final int SECRET_BYTES = 20;
    private static final int DIGITS = 6;
    private static final int MODULUS = 1000000;
    private static final String MAC_ALGORITHM = "HmacSHA1";

    private Totp() {}

    /** Returns a fresh 160-bit HMAC secret. */
    public static byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    /** Returns the six-digit code for the given wall-clock seconds. */
    public static String code(byte[] secret, long epochSeconds) {
        return codeAtCounter(secret, Math.floorDiv(epochSeconds, STEP_SECONDS));
    }

    /**
     * Returns the matching counter, or -1 when the code is invalid, outside the window, or replays
     * a counter that was already used.
     */
    public static int verify(byte[] secret, String supplied, long epochSeconds, long lastCounter) {
        if (supplied == null) {
            return -1;
        }
        byte[] suppliedBytes = supplied.getBytes(StandardCharsets.US_ASCII);
        long current = Math.floorDiv(epochSeconds, STEP_SECONDS);
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            long counter = current + offset;
            if (counter < 0) {
                continue;
            }
            byte[] expected = codeAtCounter(secret, counter).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, suppliedBytes)) {
                if (counter <= lastCounter) {
                    return -1;
                }
                return (int) counter;
            }
        }
        return -1;
    }

    private static String codeAtCounter(byte[] secret, long counter) {
        byte[] message = new byte[8];
        for (int index = 0; index < 8; index++) {
            message[7 - index] = (byte) (counter >>> (8 * index));
        }
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, MAC_ALGORITHM));
            byte[] digest = mac.doFinal(message);
            int offset = digest[digest.length - 1] & 0x0f;
            int binary =
                    ((digest[offset] & 0x7f) << 24)
                            | ((digest[offset + 1] & 0xff) << 16)
                            | ((digest[offset + 2] & 0xff) << 8)
                            | (digest[offset + 3] & 0xff);
            return pad(binary % MODULUS);
        } catch (GeneralSecurityException failure) {
            throw new LocalAuthException("TOTP calculation failed", failure);
        }
    }

    private static String pad(int value) {
        String digits = Integer.toString(value);
        StringBuilder padded = new StringBuilder();
        while (padded.length() + digits.length() < DIGITS) {
            padded.append('0');
        }
        padded.append(digits);
        return padded.toString();
    }
}
