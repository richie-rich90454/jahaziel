/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * OS abstraction. All {@code os.name}/{@code os.arch} reads live in {@link Platforms}; every other
 * module talks to this interface.
 */
public interface Platform {
  /** Supported operating systems. */
  enum Os {
    WINDOWS,
    MACOS,
    LINUX
  }

  /** Supported architectures. */
  enum Arch {
    X64,
    ARM64
  }

  /** Returns the OS. */
  Os os();

  /** Returns the architecture. */
  Arch arch();

  /** Returns the directory holding the running jar. */
  Path jarDir();

  /** Returns the data root ({@code jarDir/data} or {@code --data-dir}). */
  Path dataDir();

  /** Returns {@code data/tmp}. */
  Path tempDir();

  /** Returns the REH (IDE server) directory for this platform. */
  Path rehDir();

  /** Returns one of win32-x64, win32-arm64, darwin-x64, darwin-arm64, linux-x64, linux-arm64. */
  String rehVariant();

  /** Returns the REH executable. */
  Path rehExecutable();

  /**
   * Builds the REH launch arguments for a workspace and port.
   *
   * @param workspace the workspace directory
   * @param port the port to listen on
   * @return the arguments, without the executable
   */
  List<String> rehLaunchArgs(Path workspace, int port);

  /**
   * Builds the native terminal command for a working directory.
   *
   * @param workingDir the directory to open in
   * @return the command, executable first
   */
  List<String> terminal(Path workingDir);

  /** Returns the PATH separator ({@code ;} on Windows, {@code :} elsewhere). */
  String pathSeparator();

  /**
   * Converts a path to native separators.
   *
   * @param path the path
   * @return the native string
   */
  String toNative(Path path);

  /**
   * Converts a path to POSIX separators (forward slashes). Used for JDBC URLs and env values that
   * must survive backslash hosts.
   *
   * @param path the path
   * @return the forward-slash string
   */
  String toPosix(Path path);

  /**
   * Returns the subprocess environment: the current process env with TEMP/TMP/TMPDIR all pointing
   * at {@code data/tmp}.
   *
   * @return an unmodifiable map
   */
  Map<String, String> subprocessEnv();
}
