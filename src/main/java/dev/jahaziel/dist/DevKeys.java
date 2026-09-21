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
      Path lock = keys.resolve(".lock");
      boolean mine;
      try {
        Files.createFile(lock);
        mine = true;
      } catch (java.nio.file.FileAlreadyExistsException held) {
        mine = false;
      }
      if (!mine) {
        for (int attempt = 0; attempt < 100; attempt++) {
          if (Files.isRegularFile(privateKey) && Files.isRegularFile(publicKey)) {
            return new Keys(privateKey, publicKey);
          }
          try {
            Thread.sleep(100);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            break;
          }
        }
        throw new IllegalStateException("key generation is stuck in " + keys);
      }
      try {
        if (Files.isRegularFile(privateKey) && Files.isRegularFile(publicKey)) {
          return new Keys(privateKey, publicKey);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        writePem(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
        writePem(publicKey, "PUBLIC KEY", pair.getPublic().getEncoded());
        return new Keys(privateKey, publicKey);
      } finally {
        Files.deleteIfExists(lock);
      }
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
      Path staged = Files.createTempFile(path.toAbsolutePath().getParent(), "key-", ".pem");
      try {
        Files.writeString(staged, pem);
        try {
          Files.setPosixFilePermissions(
              staged, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException onWindows) {
          staged.toFile().setReadable(false, false);
          staged.toFile().setReadable(true, true);
          staged.toFile().setWritable(false, false);
          staged.toFile().setWritable(true, true);
        }
        try {
          Files.move(
              staged,
              path,
              java.nio.file.StandardCopyOption.ATOMIC_MOVE,
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
          Files.move(staged, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (IOException | RuntimeException failure) {
        Files.deleteIfExists(staged);
        throw failure;
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot write key " + path, failure);
    }
  }
}
