/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link JarDataDir} layout creation and SQLite preservation. */
class JarDataDirTest {
  @TempDir private Path tmp;

  @Test
  void createsLayoutAndNeverClearsDb() throws Exception {
    Path explicit = tmp.resolve("srv");
    JarDataDir first = JarDataDir.resolve(explicit.toString());
    assertTrue(Files.isDirectory(first.workspaces()));
    assertTrue(Files.isDirectory(first.images()));
    assertTrue(Files.isDirectory(first.jail()));
    assertTrue(Files.isDirectory(first.audit()));
    assertTrue(Files.isDirectory(first.sync()));
    assertTrue(Files.isDirectory(first.keys()));
    assertTrue(Files.isDirectory(first.logs()));
    assertTrue(Files.isRegularFile(first.db()));

    Files.writeString(first.db(), "sentinel");
    JarDataDir second = JarDataDir.resolve(explicit.toString());
    assertEquals("sentinel", Files.readString(second.db()));
    assertEquals(first.root(), second.root());
  }

  @Test
  void defaultsNextToWorkingDirectoryOutsideJar() {
    String home = System.getProperty("user.dir");
    System.setProperty("user.dir", tmp.toString());
    try {
      JarDataDir data = JarDataDir.resolve(null);
      assertEquals(tmp.resolve("data").toAbsolutePath().normalize(), data.root());
      assertTrue(Files.isRegularFile(data.db()));
    } finally {
      System.setProperty("user.dir", home);
    }
  }

  @Test
  void adjacentBaseTracksTheLauncherJarOrFallsBackToWorkingDirectory() {
    // Spring Boot 4 launches from a `jar:nested:` URL, so the base must come from
    // ApplicationHome rather than a hand-parsed code source URI.
    var source = new org.springframework.boot.system.ApplicationHome(JarDataDir.class).getSource();
    String base = JarDataDir.adjacentBase();
    if (source != null && source.getName().endsWith(".jar")) {
      assertEquals(source.getAbsoluteFile().getParent(), Path.of(base).toString());
    } else {
      assertEquals(System.getProperty("user.dir"), base);
    }
  }
}
