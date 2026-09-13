/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * Development and single-node session keys. Generates an RSA pair into the data directory on first
 * run and reuses it afterwards, so tokens survive restarts without any operator step. Formats match
 * the control-plane and gateway PEM loaders exactly.
 */
public final class DevKeys {
  private DevKeys() {}

  /**
   * Ensures the key pair exists.
   *
   * @param keys the key directory
   * @return the private and public key paths
   */
  public static Keys ensure(Path keys) {
    Objects.requireNonNull(keys, "keys");
    Path privateKey = keys.resolve("jwt.key");
    Path publicKey = keys.resolve("jwt.pub");
    if (Files.isRegularFile(privateKey) && Files.isRegularFile(publicKey)) {
      return new Keys(privateKey, publicKey);
    }
    try {
      Files.createDirectories(keys);
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      writePem(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
      writePem(publicKey, "PUBLIC KEY", pair.getPublic().getEncoded());
      return new Keys(privateKey, publicKey);
    } catch (Exception failure) {
      throw new IllegalStateException("cannot generate session keys in " + keys, failure);
    }
  }

  /** Key file locations. */
  public record Keys(Path privateKey, Path publicKey) {
    public Keys {
      Objects.requireNonNull(privateKey, "privateKey");
      Objects.requireNonNull(publicKey, "publicKey");
    }
  }

  private static void writePem(Path path, String kind, byte[] encoded) {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded) + "\n";
    String pem = "-----BEGIN " + kind + "-----\n" + body + "-----END " + kind + "-----\n";
    try {
      Files.writeString(path, pem);
      try {
        Files.setPosixFilePermissions(
            path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      } catch (UnsupportedOperationException onWindows) {
        path.toFile().setReadable(false, false);
        path.toFile().setReadable(true, true);
        path.toFile().setWritable(false, false);
        path.toFile().setWritable(true, true);
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot write key " + path, failure);
    }
  }
}
