/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Jar-adjacent data directory. Everything the fat jar owns lives under {@code data/} next to the
 * jar: workspaces, images, jails, audit spools, sync state, keys, logs, and the SQLite file. The
 * layout is created on demand and never deleted by the runtime.
 */
public final class JarDataDir {
  /** SQLite file name inside the data root. */
  public static final String DB_FILE = "jahaziel.db";

  private final Path root;

  private JarDataDir(Path root) {
    this.root = root;
  }

  /**
   * Resolves the data directory.
   *
   * @param explicit the --data-dir value, or null for jar-adjacent
   * @return the layout, with directories created and the SQLite file touched
   */
  public static JarDataDir resolve(String explicit) {
    Path root =
        explicit == null || explicit.isBlank()
            ? Path.of(adjacentBase(), "data")
            : Path.of(explicit);
    JarDataDir layout = new JarDataDir(root.toAbsolutePath().normalize());
    layout.ensure();
    return layout;
  }

  /** Returns the data root. */
  public Path root() {
    return root;
  }

  /** Returns the workspace homes parent. */
  public Path workspaces() {
    return root.resolve("workspaces");
  }

  /** Returns the base images directory. */
  public Path images() {
    return root.resolve("images");
  }

  /** Returns the per-workspace jail parent. */
  public Path jail() {
    return root.resolve("jail");
  }

  /** Returns the audit staging directory. */
  public Path audit() {
    return root.resolve("audit");
  }

  /** Returns the sync state directory. */
  public Path sync() {
    return root.resolve("sync");
  }

  /** Returns the key directory. */
  public Path keys() {
    return root.resolve("keys");
  }

  /** Returns the log directory. */
  public Path logs() {
    return root.resolve("logs");
  }

  /** Returns the SQLite file, created when absent and never cleared. */
  public Path db() {
    return root.resolve(DB_FILE);
  }

  private void ensure() {
    try {
      Files.createDirectories(workspaces());
      Files.createDirectories(images());
      Files.createDirectories(jail());
      Files.createDirectories(audit());
      Files.createDirectories(sync());
      Files.createDirectories(keys());
      Files.createDirectories(logs());
      try {
        Files.createFile(db());
      } catch (java.nio.file.FileAlreadyExistsException concurrentBoot) {
        // Another process won the cold-boot race; the file is there.
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot prepare data directory " + root, failure);
    }
  }

  static String adjacentBase() {
    try {
      var location = JarDataDir.class.getProtectionDomain().getCodeSource().getLocation();
      if (location != null) {
        Path jar = Path.of(location.toURI());
        String file = jar.getFileName().toString();
        if (file.endsWith(".jar")) {
          Path parent = jar.toAbsolutePath().getParent();
          if (parent != null) {
            return parent.toString();
          }
        }
      }
    } catch (SecurityException | URISyntaxException | IllegalArgumentException ignored) {
      // Fall through to the working directory below.
    }
    return Objects.requireNonNullElse(System.getProperty("user.dir"), ".");
  }
}
