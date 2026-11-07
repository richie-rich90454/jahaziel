/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Windows implementation of {@link Platform}. */
public final class WindowsPlatform implements Platform {
  private final Path jarDir;
  private final Path dataDir;
  private final Arch arch;

  /**
   * Creates the platform.
   *
   * @param jarDir the jar directory
   * @param dataDir the data root
   * @param arch the architecture
   */
  public WindowsPlatform(Path jarDir, Path dataDir, Arch arch) {
    this.jarDir = Objects.requireNonNull(jarDir, "jarDir").toAbsolutePath().normalize();
    this.dataDir = Objects.requireNonNull(dataDir, "dataDir").toAbsolutePath().normalize();
    this.arch = Objects.requireNonNull(arch, "arch");
  }

  @Override
  public Os os() {
    return Os.WINDOWS;
  }

  @Override
  public Arch arch() {
    return arch;
  }

  @Override
  public Path jarDir() {
    return jarDir;
  }

  @Override
  public Path dataDir() {
    return dataDir;
  }

  @Override
  public Path tempDir() {
    return dataDir.resolve("tmp");
  }

  @Override
  public String pathSeparator() {
    return ";";
  }

  @Override
  public String toNative(Path path) {
    Objects.requireNonNull(path, "path");
    return path.toString().replace('/', '\\');
  }

  @Override
  public String toPosix(Path path) {
    Objects.requireNonNull(path, "path");
    return path.toString().replace('\\', '/');
  }

  @Override
  public Map<String, String> subprocessEnv() {
    Map<String, String> env = new LinkedHashMap<>(System.getenv());
    String tmp = toNative(tempDir());
    env.put("TEMP", tmp);
    env.put("TMP", tmp);
    env.put("TMPDIR", tmp);
    return Collections.unmodifiableMap(env);
  }
}
