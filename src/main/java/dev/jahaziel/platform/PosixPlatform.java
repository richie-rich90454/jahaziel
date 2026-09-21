/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** POSIX implementation (macOS and Linux) of {@link Platform}. */
public final class PosixPlatform implements Platform {
  private final Os os;
  private final Arch arch;
  private final Path jarDir;
  private final Path dataDir;

  /**
   * Creates the platform.
   *
   * @param os the OS, MACOS or LINUX
   * @param arch the architecture
   * @param jarDir the jar directory
   * @param dataDir the data root
   */
  public PosixPlatform(Os os, Arch arch, Path jarDir, Path dataDir) {
    if (os != Os.MACOS && os != Os.LINUX) {
      throw new IllegalArgumentException("posix only: " + os);
    }
    this.os = os;
    this.arch = Objects.requireNonNull(arch, "arch");
    this.jarDir = Objects.requireNonNull(jarDir, "jarDir").toAbsolutePath().normalize();
    this.dataDir = Objects.requireNonNull(dataDir, "dataDir").toAbsolutePath().normalize();
  }

  @Override
  public Os os() {
    return os;
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
  public Path rehDir() {
    return dataDir.resolve("ide").resolve(rehVariant());
  }

  @Override
  public String rehVariant() {
    String prefix = os == Os.MACOS ? "darwin-" : "linux-";
    return prefix + (arch == Arch.ARM64 ? "arm64" : "x64");
  }

  @Override
  public Path rehExecutable() {
    return rehDir().resolve("bin").resolve("jahaziel-code-server");
  }

  @Override
  public List<String> rehLaunchArgs(Path workspace, int port) {
    Objects.requireNonNull(workspace, "workspace");
    List<String> args = new ArrayList<>();
    args.add("--host");
    args.add("127.0.0.1");
    args.add("--port");
    args.add(Integer.toString(port));
    args.add("--without-connection-token");
    args.add("--server-data-dir");
    args.add(toNative(dataDir()));
    args.add("--extensions-dir");
    args.add(toNative(dataDir().resolve("extensions")));
    args.add(toNative(workspace.toAbsolutePath().normalize()));
    return Collections.unmodifiableList(args);
  }

  @Override
  public List<String> terminal(Path workingDir) {
    Objects.requireNonNull(workingDir, "workingDir");
    String dir = toNative(workingDir);
    if (os == Os.MACOS) {
      return List.of("open", "-a", "Terminal", dir);
    }
    return List.of("x-terminal-emulator", "-e", "bash", "--", dir);
  }

  @Override
  public String pathSeparator() {
    return ":";
  }

  @Override
  public String toNative(Path path) {
    Objects.requireNonNull(path, "path");
    return path.toString().replace('\\', '/');
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
