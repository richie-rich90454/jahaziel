/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Runs host commands with ProcessBuilder. */
public final class ProcessCommandRunner implements CommandRunner {
  @Override
  public String run(List<String> command) {
    Objects.requireNonNull(command, "command");
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes()).trim();
      int exit = process.waitFor();
      if (exit != 0) {
        throw new StorageException(String.join(" ", command), exit, output);
      }
      return output;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new StorageException(String.join(" ", command), failure);
    } catch (IOException failure) {
      throw new StorageException(String.join(" ", command), failure);
    }
  }

  @Override
  public boolean exists(Path path) {
    Objects.requireNonNull(path, "path");
    // `test -e` is a shell builtin, not an executable, so ProcessBuilder cannot run it
    // portably; on Windows it always failed. Files.exists is the same check natively.
    return Files.exists(path);
  }
}
