/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Random login values and the S256 PKCE challenge. */
public final class Pkce {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {}

    /** Returns an opaque 43-character value suitable for state, nonce, or a verifier. */
    public static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** Returns the base64url SHA-256 challenge for a verifier. */
    public static String challenge(String verifier) {
        if (verifier == null || verifier.length() == 0) {
            throw new BasisException("missing PKCE verifier");
        }
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new BasisException("SHA-256 is unavailable", impossible);
        }
    }

    /** Returns the left-half at_hash for an access token, as required by OIDC. */
    public static String atHash(String accessToken) {
        if (accessToken == null || accessToken.length() == 0) {
            return null;
        }
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            byte[] half = new byte[16];
            System.arraycopy(digest, 0, half, 0, half.length);
            return ENCODER.encodeToString(half);
        } catch (NoSuchAlgorithmException impossible) {
            throw new BasisException("SHA-256 is unavailable", impossible);
        }
    }
}
