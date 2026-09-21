/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Detects the current {@link Platform}. The only place that reads {@code os.name} or {@code
 * os.arch}; everything else injects a {@link Platform}.
 */
public final class Platforms {
  /** Supported REH variants, printed when the host is unsupported. */
  public static final List<String> SUPPORTED_VARIANTS =
      List.of("win32-x64", "win32-arm64", "darwin-x64", "darwin-arm64", "linux-x64", "linux-arm64");

  private Platforms() {}

  /**
   * Returns the REH slug for this host, for example {@code linux-x64}. Pure host mapping, no
   * Spring, so JDK-only callers like the CLI can use it without pulling in the jar layout.
   *
   * @return the variant slug
   */
  public static String hostRehVariant() {
    String osName = System.getProperty("os.name", "");
    String osArch = System.getProperty("os.arch", "");
    boolean arm =
        osArch.toLowerCase(Locale.ROOT).contains("aarch64")
            || osArch.toLowerCase(Locale.ROOT).contains("arm64");
    String sys =
        mapOs(osName) == Platform.Os.WINDOWS
            ? "win32"
            : mapOs(osName) == Platform.Os.MACOS ? "darwin" : "linux";
    return sys + "-" + (arm ? "arm64" : "x64");
  }

  /**
   * Detects the platform from the real JVM properties and the jar location. Unsupported hosts
   * throw with the detected values plus the supported combos; the jar entry point maps that to
   * exit 1.
   *
   * @param explicitDataDir the --data-dir value, or null for jar-adjacent
   * @return the platform
   */
  public static Platform detect(String explicitDataDir) {
    return detect(
        System.getProperty("os.name", ""),
        System.getProperty("os.arch", ""),
        resolveJarDir(),
        explicitDataDir);
  }

  /**
   * Testable detection from explicit values. Unsupported hosts throw.
   *
   * @param osName the os.name value
   * @param osArch the os.arch value
   * @param jarDir the jar directory
   * @param explicitDataDir the --data-dir value, or null
   * @return the platform
   */
  public static Platform detect(String osName, String osArch, Path jarDir, String explicitDataDir) {
    Objects.requireNonNull(jarDir, "jarDir");
    Platform.Os os = mapOs(osName);
    Platform.Arch arch = mapArch(osArch);
    if (os == null || arch == null) {
      throw new IllegalStateException(
          "unsupported platform: os.name="
              + osName
              + " os.arch="
              + osArch
              + " (detected os="
              + os
              + " arch="
              + arch
              + "). supported: "
              + String.join(", ", SUPPORTED_VARIANTS));
    }
    Path dataDir =
        explicitDataDir == null || explicitDataDir.isBlank()
            ? jarDir.resolve("data")
            : Path.of(explicitDataDir);
    if (os == Platform.Os.WINDOWS) {
      return new WindowsPlatform(jarDir, dataDir, arch);
    }
    return new PosixPlatform(os, arch, jarDir, dataDir);
  }

  static Platform.Os mapOs(String osName) {
    String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
    // NOTE: darwin contains "win", so mac must be checked before windows.
    if (name.contains("mac") || name.contains("darwin")) {
      return Platform.Os.MACOS;
    }
    if (name.contains("win")) {
      return Platform.Os.WINDOWS;
    }
    if (name.contains("nix") || name.contains("nux") || name.contains("linux")) {
      return Platform.Os.LINUX;
    }
    return null;
  }

  static Platform.Arch mapArch(String osArch) {
    String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
    if (arch.equals("amd64")
        || arch.equals("x86_64")
        || arch.equals("x64")
        || arch.equals("x86-64")) {
      return Platform.Arch.X64;
    }
    if (arch.equals("aarch64") || arch.equals("arm64")) {
      return Platform.Arch.ARM64;
    }
    return null;
  }

  /**
   * Resolves the jar directory from the code source, never from {@code user.home}. Uses Spring
   * Boot's {@code ApplicationHome} first so {@code jar:file:} and {@code jar:nested:} launchers
   * resolve next to the jar; falls back to the raw code source URI and finally to {@code user.dir}
   * only when running unpacked (IDE/tests), where no jar exists.
   *
   * @return the jar directory
   */
  public static Path resolveJarDir() {
    try {
      var source = new org.springframework.boot.system.ApplicationHome(Platforms.class).getSource();
      if (source != null && source.getName().endsWith(".jar")) {
        var parent = source.getAbsoluteFile().getParentFile();
        if (parent != null) {
          return parent.toPath().toAbsolutePath().normalize();
        }
      }
    } catch (SecurityException | LinkageError ignored) {
      // Fall through to the raw code source below.
    }
    try {
      var codeSource = Platforms.class.getProtectionDomain().getCodeSource();
      if (codeSource != null && codeSource.getLocation() != null) {
        Path location = Path.of(codeSource.getLocation().toURI());
        if (location.getFileName() != null && location.toString().endsWith(".jar")) {
          Path parent = location.toAbsolutePath().getParent();
          if (parent != null) {
            return parent.normalize();
          }
        }
      }
    } catch (URISyntaxException | SecurityException | IllegalArgumentException ignored) {
      // Fall through to the unpacked fallback below.
    }
    return Path.of(Objects.requireNonNullElse(System.getProperty("user.dir"), "."))
        .toAbsolutePath()
        .normalize();
  }
}
