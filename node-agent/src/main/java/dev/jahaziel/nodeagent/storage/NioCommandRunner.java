/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

/**
 * Pure-Java {@link CommandRunner} for the exact argv shapes {@link FolderStorage} uses. No
 * subprocesses, so workspaces work on Windows, Windows Server, macOS, and Linux alike. Anything
 * outside those shapes fails fast instead of half-running.
 */
public final class NioCommandRunner implements CommandRunner {
  @Override
  public String run(List<String> command) {
    Objects.requireNonNull(command, "command");
    if (command.size() == 3 && command.get(0).equals("mkdir") && command.get(1).equals("-p")) {
      mkdir(Path.of(command.get(2)));
      return "";
    }
    if (command.size() == 3 && command.get(0).equals("rm") && command.get(1).equals("-rf")) {
      deleteRecursive(Path.of(command.get(2)));
      return "";
    }
    if (command.size() == 3 && command.get(0).equals("mv")) {
      move(Path.of(command.get(1)), Path.of(command.get(2)));
      return "";
    }
    if (command.size() == 4 && command.get(0).equals("cp") && command.get(1).equals("-a")) {
      copyContentsOf(Path.of(command.get(2)), Path.of(command.get(3)));
      return "";
    }
    if (command.size() == 4 && command.get(0).equals("chmod") && command.get(1).equals("-R")) {
      chmod(Path.of(command.get(3)), command.get(2));
      return "";
    }
    if (command.size() == 3 && command.get(0).equals("du") && command.get(1).equals("-sb")) {
      Path dir = Path.of(command.get(2));
      return sizeOf(dir) + "\t" + dir;
    }
    throw new StorageException(String.join(" ", command), -1, "unsupported command for NIO runner");
  }

  @Override
  public boolean exists(Path path) {
    Objects.requireNonNull(path, "path");
    return Files.exists(path);
  }

  private static void mkdir(Path dir) {
    try {
      Files.createDirectories(dir);
    } catch (IOException failure) {
      throw new StorageException("mkdir -p " + dir, failure);
    }
  }

  static void deleteRecursive(Path target) {
    if (!Files.exists(target) && !Files.isSymbolicLink(target)) {
      return;
    }
    try {
      Files.walkFileTree(
          target,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              makeWritable(file);
              try {
                Files.deleteIfExists(file);
              } catch (IOException failure) {
                throw new UncheckedIOException(failure);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) {
              if (failure != null) {
                throw new UncheckedIOException(failure);
              }
              makeWritable(dir);
              try {
                Files.deleteIfExists(dir);
              } catch (IOException delete) {
                throw new UncheckedIOException(delete);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (UncheckedIOException failure) {
      throw new StorageException("rm -rf " + target, failure.getCause());
    } catch (IOException failure) {
      throw new StorageException("rm -rf " + target, failure);
    }
  }

  private static void makeWritable(Path path) {
    try {
      path.toFile().setWritable(true, false);
    } catch (RuntimeException ignored) {
      // Best effort; the delete below reports the real outcome.
    }
  }

  private static void move(Path from, Path to) {
    try {
      Files.createDirectories(to.toAbsolutePath().getParent());
      try {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException failure) {
      throw new StorageException("mv " + from + " " + to, failure);
    }
  }

  private static void copyContentsOf(Path source, Path dest) {
    String raw = source.toString();
    Path root =
        raw.endsWith("/.")
            ? Path.of(raw.substring(0, raw.length() - 2))
            : raw.endsWith("\\.") ? Path.of(raw.substring(0, raw.length() - 2)) : source;
    try {
      Files.createDirectories(dest);
      if (!Files.isDirectory(root)) {
        return;
      }
      try (Stream<Path> walk = Files.walk(root)) {
        for (Path path : walk.sorted().toList()) {
          Path relative = root.relativize(path);
          if (relative.toString().isEmpty()) {
            continue;
          }
          Path target = dest.resolve(relative.toString());
          if (Files.isSymbolicLink(path)) {
            continue;
          }
          if (Files.isDirectory(path)) {
            Files.createDirectories(target);
          } else {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.copy(
                path,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
          }
        }
      }
    } catch (IOException failure) {
      throw new StorageException("cp -a " + source + " " + dest, failure);
    }
  }

  private static void chmod(Path target, String mode) {
    boolean writable;
    if (mode.equals("u+w")) {
      writable = true;
    } else if (mode.equals("a-w")) {
      writable = false;
    } else {
      throw new StorageException(
          "chmod -R " + mode + " " + target, -1, "unsupported mode for NIO runner");
    }
    if (!Files.exists(target)) {
      return;
    }
    try {
      Set<PosixFilePermission> permissions =
          Files.getPosixFilePermissions(target, java.nio.file.LinkOption.NOFOLLOW_LINKS);
      Set<PosixFilePermission> updated = EnumSet.copyOf(permissions);
      if (writable) {
        updated.add(PosixFilePermission.OWNER_WRITE);
      } else {
        updated.remove(PosixFilePermission.OWNER_WRITE);
        updated.remove(PosixFilePermission.GROUP_WRITE);
        updated.remove(PosixFilePermission.OTHERS_WRITE);
      }
      final Set<PosixFilePermission> frozen = Set.copyOf(updated);
      Files.walkFileTree(
          target,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              apply(file, frozen, writable);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) {
              apply(dir, frozen, writable);
              return FileVisitResult.CONTINUE;
            }

            private void apply(Path path, Set<PosixFilePermission> wanted, boolean write) {
              try {
                Files.setPosixFilePermissions(path, wanted);
              } catch (UnsupportedOperationException fallback) {
                path.toFile().setWritable(write, false);
              } catch (IOException ignored) {
                path.toFile().setWritable(write, false);
              }
            }
          });
    } catch (UnsupportedOperationException fallback) {
      target.toFile().setWritable(writable, false);
    } catch (IOException failure) {
      throw new StorageException("chmod -R " + mode + " " + target, failure);
    }
  }

  static long sizeOf(Path dir) {
    if (!Files.exists(dir)) {
      return 0;
    }
    LongAdder total = new LongAdder();
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.filter(Files::isRegularFile)
          .filter(path -> !Files.isSymbolicLink(path))
          .forEach(
              path -> {
                try {
                  total.add(Files.size(path));
                } catch (IOException unreadable) {
                  // Unreadable file contributes nothing; the quota check stays conservative.
                }
              });
    } catch (IOException failure) {
      throw new StorageException("du -sb " + dir, failure);
    }
    return total.sum();
  }
}
