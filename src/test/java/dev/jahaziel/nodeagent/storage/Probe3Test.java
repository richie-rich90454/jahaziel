/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** Temporary probe for mockStatic Files behavior. */
class Probe3Test {
  @TempDir private Path tmp;

  @Test
  void probeStubFires() throws Exception {
    Path file = tmp.resolve("f.txt");
    Files.writeString(file, "x");
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files
          .when(() -> Files.deleteIfExists(Mockito.<Path>any()))
          .thenThrow(new IOException("locked"));
      try {
        boolean result = Files.deleteIfExists(file);
        System.out.println("PROBE direct call returned=" + result + " (stub did NOT fire)");
      } catch (IOException e) {
        System.out.println("PROBE direct call threw (stub fired)");
      }
      System.out.println("PROBE file still exists=" + Files.exists(file));
    }
  }
}
