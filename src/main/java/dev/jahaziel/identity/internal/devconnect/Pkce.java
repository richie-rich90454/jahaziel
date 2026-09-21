/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** PKCE S256 secrets plus the state and nonce values that bind one login transaction. */
public final class Pkce {
  private static final SecureRandom RANDOM = new SecureRandom();

  private Pkce() {}

  /**
   * Creates a code verifier: 32 random bytes as 43 URL-safe characters.
   *
   * @return the verifier
   */
  public static String verifier() {
    return randomToken();
  }

  /**
   * Creates an opaque login state.
   *
   * @return the state
   */
  public static String state() {
    return randomToken();
  }

  /**
   * Creates an ID token nonce.
   *
   * @return the nonce
   */
  public static String nonce() {
    return randomToken();
  }

  /**
   * Derives the S256 challenge of a verifier.
   *
   * @param verifier the code verifier
   * @return the base64url SHA-256 digest
   */
  public static String challenge(String verifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static String randomToken() {
    byte[] buffer = new byte[32];
    RANDOM.nextBytes(buffer);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
  }
}
