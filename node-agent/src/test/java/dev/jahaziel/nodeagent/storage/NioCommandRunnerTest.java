/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link NioCommandRunner} against a temp directory. */
class NioCommandRunnerTest {
  private Path root;
  private NioCommandRunner commands;

  @BeforeEach
  void setUp() throws Exception {
    root = Files.createTempDirectory("nio-runner-test");
    commands = new NioCommandRunner();
  }

  @AfterEach
  void tearDown() throws Exception {
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  path.toFile().setWritable(true, false);
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // Best-effort test cleanup.
                }
              });
    }
  }

  @Test
  void mkdirExistsAndRm() {
    Path dir = root.resolve("a/b");
    assertFalse(commands.exists(dir));
    assertEquals("", commands.run(List.of("mkdir", "-p", dir.toString())));
    assertTrue(commands.exists(dir));
    assertEquals("", commands.run(List.of("rm", "-rf", root.resolve("a").toString())));
    assertFalse(commands.exists(root.resolve("a")));
    assertEquals("", commands.run(List.of("rm", "-rf", root.resolve("missing").toString())));
  }

  @Test
  void copyMoveAndDu() throws Exception {
    Path src = root.resolve("src");
    Files.createDirectories(src.resolve("sub"));
    Files.writeString(src.resolve("top.txt"), "hello");
    Files.writeString(src.resolve("sub/nested.txt"), "world!");
    Path dest = root.resolve("dest");
    assertEquals("", commands.run(List.of("cp", "-a", src + "/.", dest.toString())));
    assertEquals("hello", Files.readString(dest.resolve("top.txt")));
    assertEquals("world!", Files.readString(dest.resolve("sub/nested.txt")));
    String output = commands.run(List.of("du", "-sb", dest.toString()));
    assertEquals(11L, FolderStorage.parseDu(output));
    Path moved = root.resolve("moved");
    assertEquals("", commands.run(List.of("mv", dest.toString(), moved.toString())));
    assertFalse(commands.exists(dest));
    assertTrue(commands.exists(moved.resolve("top.txt")));
  }

  @Test
  void chmodFreezesAndUnfreezes() throws Exception {
    Path snap = root.resolve("snap");
    Files.createDirectories(snap);
    Files.writeString(snap.resolve("f.txt"), "data");
    assertEquals("", commands.run(List.of("chmod", "-R", "a-w", snap.toString())));
    assertEquals("", commands.run(List.of("chmod", "-R", "u+w", snap.toString())));
    Files.writeString(snap.resolve("f.txt"), "changed");
    assertEquals("changed", Files.readString(snap.resolve("f.txt")));
  }

  @Test
  void rejectsUnknownShapes() {
    assertThrows(StorageException.class, () -> commands.run(List.of("rm", "x")));
    assertThrows(
        StorageException.class, () -> commands.run(List.of("chmod", "-R", "u+x", root.toString())));
    assertThrows(NullPointerException.class, () -> commands.run(null));
    assertThrows(NullPointerException.class, () -> commands.exists(null));
  }
}
