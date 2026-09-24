/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Branch toggles of {@link NioCommandRunner}. */
class NioCoverageTest {
  @TempDir private Path root;

  private final NioCommandRunner runner = new NioCommandRunner();

  @Test
  void shapeBranches() {
    assertThrows(StorageException.class, () -> runner.run(List.of("mkdir", "-p")));
    assertThrows(StorageException.class, () -> runner.run(List.of("rm", "-p", "x")));
    assertThrows(StorageException.class, () -> runner.run(List.of("mkdir", "-x", "y")));
    assertThrows(StorageException.class, () -> runner.run(List.of("rm", "x")));
    assertThrows(StorageException.class, () -> runner.run(List.of("rm", "-rf", "a", "extra")));
    assertThrows(StorageException.class, () -> runner.run(List.of("mv", "a")));
    assertThrows(StorageException.class, () -> runner.run(List.of("mv", "a", "b", "c")));
    assertThrows(StorageException.class, () -> runner.run(List.of("cp", "-a", "a")));
    assertThrows(StorageException.class, () -> runner.run(List.of("cp", "-x", "a", "b")));
    assertThrows(StorageException.class, () -> runner.run(List.of("cp", "a", "b", "c", "d")));
    assertThrows(StorageException.class, () -> runner.run(List.of("chmod", "-R", "u+w")));
    assertThrows(StorageException.class, () -> runner.run(List.of("chmod", "-x", "u+w", "x")));
    assertThrows(StorageException.class, () -> runner.run(List.of("du", "-sb")));
    assertThrows(StorageException.class, () -> runner.run(List.of("du", "-x", "y")));
    assertThrows(StorageException.class, () -> runner.run(List.of("du", "-sb", "a", "b")));
  }

  @Test
  void mkdirFailsUnderFile() throws Exception {
    Path file = root.resolve("block");
    Files.writeString(file, "x");
    Path child = file.resolve("child");
    assertThrows(
        StorageException.class, () -> runner.run(List.of("mkdir", "-p", child.toString())));
  }

  @Test
  void moveMissingSourceFails() {
    assertThrows(
        StorageException.class,
        () ->
            runner.run(
                List.of("mv", root.resolve("absent").toString(), root.resolve("out").toString())));
  }

  @Test
  void copyVariants() throws Exception {
    Path src = root.resolve("src");
    Files.createDirectories(src);
    Files.writeString(src.resolve("f.txt"), "hi");
    Path plain = root.resolve("plain");
    assertEquals("", runner.run(List.of("cp", "-a", src.toString(), plain.toString())));
    assertTrue(Files.isRegularFile(plain.resolve("f.txt")));
    Path fileSrc = root.resolve("single.txt");
    Files.writeString(fileSrc, "x");
    Path fileDest = root.resolve("fileDest");
    assertEquals("", runner.run(List.of("cp", "-a", fileSrc.toString(), fileDest.toString())));
    assertTrue(Files.isDirectory(fileDest));
    Path backslash = root.resolve("back");
    assertEquals("", runner.run(List.of("cp", "-a", src + "\\.", backslash.toString())));
    assertTrue(Files.isRegularFile(backslash.resolve("f.txt")));
  }

  @Test
  void copyFailsUnderFile() throws Exception {
    Path file = root.resolve("block2");
    Files.writeString(file, "x");
    Path src = root.resolve("src2");
    Files.createDirectories(src);
    Files.writeString(src.resolve("f.txt"), "hi");
    Path dest = file.resolve("child");
    assertThrows(
        StorageException.class, () -> runner.run(List.of("cp", "-a", src + "/.", dest.toString())));
  }

  @Test
  void chmodMissingIsNoop() {
    assertEquals("", runner.run(List.of("chmod", "-R", "u+w", root.resolve("absent").toString())));
    assertEquals("", runner.run(List.of("chmod", "-R", "a-w", root.resolve("absent2").toString())));
  }

  @Test
  void duMissingIsZero() {
    String out = runner.run(List.of("du", "-sb", root.resolve("absent-du").toString()));
    assertEquals(0L, FolderStorage.parseDu(out));
  }

  @Test
  void deleteSymlink() throws Exception {
    Path real = root.resolve("real.txt");
    Files.writeString(real, "data");
    Path link = root.resolve("link.txt");
    try {
      Files.createSymbolicLink(link, real.getFileName());
    } catch (UnsupportedOperationException | java.io.IOException skipped) {
      return;
    }
    assertEquals("", runner.run(List.of("rm", "-rf", link.toString())));
  }

  @Test
  void sizeOfIgnoresSymlink() throws Exception {
    Path dir = root.resolve("sized");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("a.txt"), "hello");
    try {
      Files.createSymbolicLink(dir.resolve("l.txt"), Path.of("a.txt"));
    } catch (UnsupportedOperationException | java.io.IOException skipped) {
      // Symlinks need privilege; size still measured.
    }
    String out = runner.run(List.of("du", "-sb", dir.toString()));
    assertEquals(5L, FolderStorage.parseDu(out));
  }
}
