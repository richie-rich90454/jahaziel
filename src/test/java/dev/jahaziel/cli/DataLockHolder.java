/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Holds a data-directory lock from another process so doctor sees a cross-process lock. */
public final class DataLockHolder {
  private DataLockHolder() {}

  /**
   * Locks the given file and sleeps.
   *
   * @param args single path to the lock file
   */
  public static void main(String[] args) throws Exception {
    Path lock = Path.of(args[0]);
    Files.createDirectories(lock.toAbsolutePath().normalize().getParent());
    try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var held = channel.lock()) {
      System.out.println("locked " + lock);
      Thread.sleep(30_000);
    }
  }
}
