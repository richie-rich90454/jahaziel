/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ide.internal;

import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Remote-extension-host builds. Selects one of six variants, verifies its tarball with SHA-256, and
 * extracts once to {@code data/ide/<variant>/} guarded by a {@code .extracted-<version>-<variant>}
 * sentinel. The gateway relays both plain HTTP and WebSocket upgrades to the extracted server's
 * port.
 */
@Service
public final class IdeService {
  /** Six REH targets: three operating systems by two architectures. */
  public enum Variant {
    LINUX_X64,
    LINUX_ARM64,
    DARWIN_X64,
    DARWIN_ARM64,
    WIN32_X64,
    WIN32_ARM64;

    /** Returns the directory and asset slug, for example {@code linux-x64}. */
    public String slug() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Selects the variant: explicit config wins, otherwise the host OS and architecture. */
    public static Variant select(String configured) {
      if (configured != null && !configured.isBlank()) {
        return Variant.valueOf(configured.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
      }
      Platform platform = Platforms.detect(null);
      boolean arm = platform.arch() == Platform.Arch.ARM64;
      return switch (platform.os()) {
        case MACOS -> arm ? DARWIN_ARM64 : DARWIN_X64;
        case WINDOWS -> arm ? WIN32_ARM64 : WIN32_X64;
        default -> arm ? LINUX_ARM64 : LINUX_X64;
      };
    }
  }

  private final Path ideDir;
  private final String version;
  private final String configuredVariant;

  public IdeService(
      @Value("${jahaziel.ide.dir:data/ide}") String ideDir,
      @Value("${jahaziel.ide.version:1.137.0}") String version,
      @Value("${jahaziel.reh:}") String configuredVariant) {
    this.ideDir = Path.of(Objects.requireNonNull(ideDir, "ideDir"));
    this.version = Objects.requireNonNull(version, "version");
    this.configuredVariant = configuredVariant;
  }

  /** Returns the active variant. */
  public Variant variant() {
    return Variant.select(configuredVariant);
  }

  /** Returns the extracted server directory, extracting once when the sentinel is absent. */
  public Path ensureExtracted() {
    Variant variant = variant();
    Path target = ideDir.resolve(variant.slug());
    Path sentinel = target.resolve(".extracted-" + version + "-" + variant.slug());
    if (Files.exists(sentinel)) {
      return target;
    }
    Path tarball = ideDir.resolve("jahaziel-code-server-" + variant.slug() + ".tar.zst");
    if (!Files.isRegularFile(tarball)) {
      throw new IllegalStateException(
          "REH tarball missing: "
              + tarball
              + " (build it with ide/build.sh or fetch the manifest)");
    }
    verify(tarball);
    try {
      Files.createDirectories(target);
      int exit =
          new ProcessBuilder(
                  "tar",
                  "--use-compress-program=zstd",
                  "-xf",
                  tarball.toString(),
                  "-C",
                  ideDir.toString())
              .inheritIO()
              .start()
              .waitFor();
      if (exit != 0) {
        throw new IllegalStateException("tar extraction failed for " + tarball);
      }
      Files.createFile(sentinel);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("REH extraction interrupted", failure);
    }
    return target;
  }

  private static void verify(Path tarball) {
    Path checksum = tarball.resolveSibling(tarball.getFileName() + ".sha256");
    if (!Files.isRegularFile(checksum)) {
      throw new IllegalStateException("refusing to extract without checksum: " + checksum);
    }
    try {
      String expected = Files.readString(checksum).strip().split("\\s+")[0];
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      try (var in = Files.newInputStream(tarball)) {
        sha256.update(in.readAllBytes());
      }
      String actual = HexFormat.of().formatHex(sha256.digest());
      if (!actual.equalsIgnoreCase(expected)) {
        throw new IllegalStateException("SHA-256 mismatch for " + tarball);
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
