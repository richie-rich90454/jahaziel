/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Failure branches of {@link NioCommandRunner} that need locked files or filesystems. */
class NioCommandRunnerCoverageGapTest {
  private final NioCommandRunner commands = new NioCommandRunner();

  @Test
  void missingTargetIsNoop(@TempDir Path root) {
    NioCommandRunner.deleteRecursive(root.resolve("absent"));
    assertFalse(Files.exists(root.resolve("absent")));
  }

  @Test
  void deleteFailsWhenFileLocked(@TempDir Path root) throws Exception {
    Path target = root.resolve("target");
    Files.createDirectories(target);
    Path victim = target.resolve("victim.txt");
    Files.writeString(victim, "x");
    List<AclEntry> victimAcl = deny(victim, AclEntryPermission.DELETE);
    List<AclEntry> targetAcl = deny(target, AclEntryPermission.DELETE_CHILD);
    try {
      assertThrows(StorageException.class, () -> rmRf(target));
      assertTrue(Files.exists(victim));
    } finally {
      restoreAcl(victim, victimAcl);
      restoreAcl(target, targetAcl);
    }
  }

  @Test
  void postVisitDeleteFailure(@TempDir Path root) throws Exception {
    Path target = root.resolve("target");
    Files.createDirectories(target.resolve("empty"));
    List<AclEntry> subAcl = deny(target.resolve("empty"), AclEntryPermission.DELETE);
    List<AclEntry> targetAcl = deny(target, AclEntryPermission.DELETE_CHILD);
    try {
      assertThrows(StorageException.class, () -> rmRf(target));
      assertTrue(Files.isDirectory(target.resolve("empty")));
    } finally {
      restoreAcl(target.resolve("empty"), subAcl);
      restoreAcl(target, targetAcl);
    }
  }

  @Test
  void walkFailureOnLockedStart(@TempDir Path root) throws Exception {
    Path target = root.resolve("target");
    Files.createDirectories(target.resolve("sub"));
    Files.writeString(target.resolve("sub/f.txt"), "hi");
    List<AclEntry> original = deny(target, AclEntryPermission.LIST_DIRECTORY);
    try {
      assertThrows(StorageException.class, () -> rmRf(target));
    } finally {
      restoreAcl(target, original);
    }
  }

  @Test
  void deleteRecursiveOnZipFs(@TempDir Path root) throws Exception {
    Path zip = root.resolve("fs.zip");
    try (FileSystem zfs =
        FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Map.of("create", "true"))) {
      Path tree = zfs.getPath("/tree");
      Files.createDirectories(tree);
      Files.writeString(tree.resolve("f.txt"), "x");
      NioCommandRunner.deleteRecursive(tree);
      assertFalse(Files.exists(tree));
    }
  }

  @Test
  void moveAcrossFilesystemsFallsBack(@TempDir Path root) throws Exception {
    Path src = root.resolve("mv.txt");
    Files.writeString(src, "payload");
    Path zip = root.resolve("mv.zip");
    try (FileSystem zfs =
        FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Map.of("create", "true"))) {
      Path dst = zfs.getPath("/moved.txt");
      Method move = NioCommandRunner.class.getDeclaredMethod("move", Path.class, Path.class);
      move.setAccessible(true);
      move.invoke(null, src, dst);
      assertEquals("payload", Files.readString(dst));
      assertFalse(Files.exists(src));
    }
  }

  @Test
  void copyAcceptsAllSourceShapes(@TempDir Path root) throws Exception {
    // ponytail: one tree, three suffix shapes; covers the "/." and "\\." branches.
    Path src = root.resolve("src");
    Files.createDirectories(src.resolve("sub"));
    Files.writeString(src.resolve("f.txt"), "hi");
    Path d1 = root.resolve("d1");
    Path d2 = root.resolve("d2");
    Path d3 = root.resolve("d3");
    assertEquals("", commands.run(List.of("cp", "-a", src + "/.", d1.toString())));
    assertEquals("", commands.run(List.of("cp", "-a", src + "\\.", d2.toString())));
    assertEquals("", commands.run(List.of("cp", "-a", src.toString(), d3.toString())));
    assertTrue(Files.isRegularFile(d1.resolve("f.txt")));
    assertTrue(Files.isRegularFile(d2.resolve("f.txt")));
    assertTrue(Files.isRegularFile(d3.resolve("f.txt")));
  }

  @Test
  void chmodWalkFailure(@TempDir Path root) throws Exception {
    Path target = root.resolve("target");
    Files.createDirectories(target);
    Files.writeString(target.resolve("f.txt"), "x");
    List<AclEntry> original = deny(target, AclEntryPermission.LIST_DIRECTORY);
    try {
      assertThrows(StorageException.class, () -> chmodW(target));
    } finally {
      restoreAcl(target, original);
    }
  }

  @Test
  void sizeOfWalkFailure(@TempDir Path root) throws Exception {
    Path dir = root.resolve("dir");
    Files.createDirectories(dir);
    List<AclEntry> original = deny(dir, AclEntryPermission.LIST_DIRECTORY);
    try {
      assertThrows(StorageException.class, () -> NioCommandRunner.sizeOf(dir));
    } finally {
      restoreAcl(dir, original);
    }
  }

  private String rmRf(Path target) {
    return commands.run(List.of("rm", "-rf", target.toString()));
  }

  private String chmodW(Path target) {
    return commands.run(List.of("chmod", "-R", "u+w", target.toString()));
  }

  private static List<AclEntry> deny(Path path, AclEntryPermission... permissions)
      throws Exception {
    AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
    assumeTrue(view != null, "ACLs unsupported on " + path);
    List<AclEntry> original = new ArrayList<>(view.getAcl());
    UserPrincipal owner = Files.getOwner(path);
    List<AclEntry> updated = new ArrayList<>(original);
    updated.add(
        0,
        AclEntry.newBuilder()
            .setType(AclEntryType.DENY)
            .setPrincipal(owner)
            .setPermissions(permissions)
            .build());
    view.setAcl(updated);
    return original;
  }

  private static void restoreAcl(Path path, List<AclEntry> original) {
    try {
      AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
      if (view != null) {
        view.setAcl(original);
      }
    } catch (Exception ignored) {
      // Best effort; the path may already be gone.
    }
  }
}
