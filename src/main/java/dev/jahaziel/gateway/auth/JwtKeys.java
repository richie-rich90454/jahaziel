/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Loads RSA public keys from X.509 PEM files. */
public final class JwtKeys {
  private JwtKeys() {}

  /**
   * Loads a public key.
   *
   * @param path the key path
   * @return the public key
   */
  public static RSAPublicKey loadPublicKey(Path path) {
    String pem = read(path);
    String stripped =
        pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
    byte[] encoded;
    try {
      encoded = Base64.getMimeDecoder().decode(stripped);
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("malformed PEM block: " + path, failure);
    }
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    } catch (Exception failure) {
      throw new IllegalArgumentException("invalid public key: " + path, failure);
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception failure) {
      throw new IllegalArgumentException("cannot read key: " + path, failure);
    }
  }
}
