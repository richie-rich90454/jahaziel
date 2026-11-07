/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.workspace.api.WorkspaceFileConflictException;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.api.WorkspaceFiles.Document;
import dev.jahaziel.workspace.api.WorkspaceFiles.Entry;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Filesystem implementation with path confinement, bounded text reads, and optimistic saves. */
public final class WorkspaceFilesImpl implements WorkspaceFiles.Operations {
  private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
  private static final int MAX_DIRECTORY_ENTRIES = 5000;
  private static final int MAX_SEARCH_FILES = 10000;
  private static final long MAX_SEARCH_BYTES = 16L * 1024 * 1024;
  private static final int MAX_SEARCH_RESULTS = 100;
  private static final Object[] SAVE_LOCKS = new Object[256];

  static {
    for (int index = 0; index < SAVE_LOCKS.length; index++) {
      SAVE_LOCKS[index] = new Object();
    }
  }

  private final Path storageRoot;
  private final WorkspaceId workspaceId;

  public WorkspaceFilesImpl(String storageRoot, WorkspaceId workspaceId) {
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
  }

  @Override
  public List<Entry> list(String relativeDirectory) {
    Path directory = resolve(relativeDirectory == null ? "." : relativeDirectory, true);
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("path is not a directory");
    }
    try (Stream<Path> entries = Files.list(directory)) {
      List<Entry> result =
          entries
              .filter(this::staysInsideRoot)
              .limit(MAX_DIRECTORY_ENTRIES + 1L)
              .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
              .map(this::entry)
              .toList();
      if (result.size() > MAX_DIRECTORY_ENTRIES) {
        throw new IllegalArgumentException("directory contains too many entries");
      }
      return result;
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Override
  public Document read(String relativePath) {
    Path file = resolve(relativePath, true);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("path is not a regular file");
    }
    try {
      long size = Files.size(file);
      if (size > MAX_TEXT_BYTES) {
        throw new IllegalArgumentException("file is too large to open in the text editor");
      }
      byte[] bytes =
          readBounded(file, MAX_TEXT_BYTES, "file is too large to open in the text editor");
      if (containsNul(bytes)) {
        throw new IllegalArgumentException("binary files cannot be opened in the text editor");
      }
      String content;
      try {
        content =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
      } catch (CharacterCodingException invalidText) {
        throw new IllegalArgumentException("file is not valid UTF-8 text");
      }
      return new Document(relativePath, content, version(bytes));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Override
  public WorkspaceFiles.Download download(String relativePath) {
    Path file = resolve(relativePath, true);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("path is not a regular file");
    }
    try {
      Path real = file.toRealPath();
      if (!real.startsWith(root())) {
        throw new IllegalArgumentException("symlink escapes the workspace");
      }
      return new WorkspaceFiles.Download(real, file.getFileName().toString(), Files.size(real));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Override
  public List<WorkspaceFiles.SearchResult> search(String query, int limit) {
    if (query == null || query.isBlank() || query.length() > 128) {
      throw new IllegalArgumentException("search query must contain 1 to 128 characters");
    }
    if (limit < 1 || limit > MAX_SEARCH_RESULTS) {
      throw new IllegalArgumentException("search result limit must be between 1 and 100");
    }
    String needle = query.strip().toLowerCase(Locale.ROOT);
    Path root = root();
    try {
      List<Path> files = searchFiles(root);
      if (files.size() > MAX_SEARCH_FILES) {
        throw new IllegalArgumentException("workspace contains too many files to search");
      }
      List<WorkspaceFiles.SearchResult> results = new java.util.ArrayList<>();
      long scannedBytes = 0;
      for (Path file : files) {
        if (results.size() >= limit || !staysInsideRoot(file)) {
          continue;
        }
        String relative =
            root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        String fileName = file.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).contains(needle)) {
          results.add(new WorkspaceFiles.SearchResult(relative, 0, "File name match"));
          continue;
        }
        long size = Files.size(file);
        if (size == 0 || size > MAX_TEXT_BYTES || scannedBytes + size > MAX_SEARCH_BYTES) {
          continue;
        }
        byte[] bytes;
        try {
          bytes = readBounded(file, MAX_TEXT_BYTES, "file is too large to search");
        } catch (IllegalArgumentException grewDuringSearch) {
          continue;
        }
        if (scannedBytes + bytes.length > MAX_SEARCH_BYTES) {
          continue;
        }
        scannedBytes += bytes.length;
        if (containsNul(bytes)) {
          continue;
        }
        String content;
        try {
          content =
              StandardCharsets.UTF_8
                  .newDecoder()
                  .onMalformedInput(CodingErrorAction.REPORT)
                  .onUnmappableCharacter(CodingErrorAction.REPORT)
                  .decode(ByteBuffer.wrap(bytes))
                  .toString();
        } catch (CharacterCodingException invalidText) {
          continue;
        }
        String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length && results.size() < limit; index++) {
          String line = lines[index];
          int match = line.toLowerCase(Locale.ROOT).indexOf(needle);
          if (match >= 0) {
            int start = Math.max(0, match - 60);
            int end = Math.min(line.length(), match + needle.length() + 100);
            String preview = line.substring(start, end).strip();
            results.add(new WorkspaceFiles.SearchResult(relative, index + 1, preview));
          }
        }
      }
      return List.copyOf(results);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot search workspace " + workspaceId, failure);
    }
  }

  private static List<Path> searchFiles(Path root) throws IOException {
    List<Path> files = new java.util.ArrayList<>();
    Files.walkFileTree(
        root,
        java.util.Set.of(),
        16,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (!directory.equals(root) && isIgnoredSearchPath(root.relativize(directory))) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (attributes.isRegularFile() && !isIgnoredSearchPath(root.relativize(file))) {
              files.add(file);
              if (files.size() > MAX_SEARCH_FILES) {
                return FileVisitResult.TERMINATE;
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return files;
  }

  @Override
  public Document save(
      String relativePath, String content, String expectedVersion, boolean createOnly) {
    Objects.requireNonNull(content, "content");
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_TEXT_BYTES) {
      throw new IllegalArgumentException("file is too large to save from the text editor");
    }
    Path file = resolve(relativePath, false);
    Path parent = file.getParent();
    if (parent == null || !Files.isDirectory(parent)) {
      throw new IllegalArgumentException("parent directory does not exist");
    }
    synchronized (saveLock(file)) {
      try {
        boolean exists = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        if (createOnly && exists) {
          throw new WorkspaceFileConflictException("file already exists");
        }
        if (!createOnly) {
          if (!exists) {
            throw new WorkspaceFileConflictException("file no longer exists");
          }
          if (expectedVersion == null || expectedVersion.isBlank()) {
            throw new WorkspaceFileConflictException(
                "If-Match is required to save an existing file");
          }
          requireVersion(file, expectedVersion, "file changed since it was opened");
        }
        Path temporary = Files.createTempFile(parent, ".jahaziel-save-", ".tmp");
        try {
          Files.write(
              temporary, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
          if (!createOnly) {
            requireVersion(
                file, expectedVersion, "file changed while the editor save was in progress");
          } else if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceFileConflictException("file already exists");
          }
          if (createOnly) {
            // ATOMIC_MOVE may replace an existing target even without REPLACE_EXISTING.
            // The ordinary move contract preserves create-only semantics under a race.
            Files.move(temporary, file);
          } else {
            try {
              Files.move(
                  temporary,
                  file,
                  StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
              Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
          }
        } finally {
          Files.deleteIfExists(temporary);
        }
        return new Document(relativePath, content, version(bytes));
      } catch (java.nio.file.FileAlreadyExistsException conflict) {
        throw new WorkspaceFileConflictException("file already exists");
      } catch (IOException failure) {
        throw new UncheckedIOException(failure);
      }
    }
  }

  private static Object saveLock(Path file) {
    int index = Math.floorMod(file.toAbsolutePath().normalize().hashCode(), SAVE_LOCKS.length);
    return SAVE_LOCKS[index];
  }

  private static void requireVersion(Path file, String expectedVersion, String message)
      throws IOException {
    String actual;
    try {
      actual = version(readBounded(file, MAX_TEXT_BYTES, "file is too large to edit"));
    } catch (IllegalArgumentException oversized) {
      throw new WorkspaceFileConflictException(message);
    }
    if (!MessageDigest.isEqual(
        actual.getBytes(StandardCharsets.US_ASCII),
        expectedVersion.replace("\"", "").getBytes(StandardCharsets.US_ASCII))) {
      throw new WorkspaceFileConflictException(message);
    }
  }

  @Override
  public void createDirectories(String relativePath) {
    Path directory = resolve(relativePath, false);
    try {
      Files.createDirectories(directory);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Override
  public void rename(String relativePath, String newRelativePath) {
    Path source = resolve(relativePath, true);
    Path destination = resolve(newRelativePath, false);
    if (source.equals(root())) {
      throw new IllegalArgumentException("workspace root cannot be renamed");
    }
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      throw new WorkspaceFileConflictException("destination already exists");
    }
    try {
      Files.move(source, destination);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Override
  public void delete(String relativePath) {
    Path target = resolve(relativePath, true);
    if (target.equals(root())) {
      throw new IllegalArgumentException("workspace root cannot be deleted");
    }
    try {
      if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
        try (Stream<Path> contents = Files.walk(target)) {
          if (contents.skip(1).findAny().isPresent()) {
            throw new IllegalArgumentException("directory must be empty before it can be deleted");
          }
        }
      }
      Files.delete(target);
    } catch (DirectoryNotEmptyException notEmpty) {
      throw new IllegalArgumentException("directory must be empty before it can be deleted");
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private Entry entry(Path path) {
    try {
      BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
      Path root = root();
      String relative =
          root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
      return new Entry(
          path.getFileName().toString(),
          relative,
          attributes.isDirectory(),
          attributes.isDirectory() ? 0 : attributes.size(),
          attributes.lastModifiedTime().toMillis());
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private Path resolve(String value, boolean mustExist) {
    if (value == null || value.isBlank() || value.indexOf('\0') >= 0 || value.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("path must be a relative workspace path");
    }
    try {
      Path requested = Path.of(value);
      if (requested.isAbsolute()) {
        throw new IllegalArgumentException("absolute paths are not allowed");
      }
      for (Path segment : requested) {
        if (segment.toString().equals("..")) {
          throw new IllegalArgumentException("parent traversal is not allowed");
        }
      }
      Path root = root();
      Path resolved = root.resolve(requested).normalize();
      if (!resolved.startsWith(root)) {
        throw new IllegalArgumentException("path escapes the workspace");
      }
      Path probe = resolved;
      while (probe != null && probe.startsWith(root)) {
        if (Files.exists(probe, LinkOption.NOFOLLOW_LINKS)) {
          Path real = probe.toRealPath();
          if (!real.startsWith(root)) {
            throw new IllegalArgumentException("symlink escapes the workspace");
          }
          break;
        }
        probe = probe.getParent();
      }
      if (mustExist && !Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalArgumentException("path does not exist");
      }
      return resolved;
    } catch (InvalidPathException failure) {
      throw new IllegalArgumentException("invalid workspace path");
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private boolean staysInsideRoot(Path path) {
    try {
      return path.toRealPath().startsWith(root());
    } catch (IOException failure) {
      return false;
    }
  }

  private static boolean isIgnoredSearchPath(Path relative) {
    for (Path segment : relative) {
      if (switch (segment.toString().toLowerCase(Locale.ROOT)) {
        case ".git", ".cache", "node_modules", "target", "dist", "build", "out" -> true;
        default -> false;
      }) {
        return true;
      }
    }
    return false;
  }

  private Path root() {
    try {
      Files.createDirectories(storageRoot);
      Path data = storageRoot.toRealPath();
      Path workspaces = data.resolve("workspaces");
      ensureDirectory(workspaces);
      Path workspace = workspaces.resolve(workspaceId.value().toString());
      ensureDirectory(workspace);
      Path home = workspace.resolve("home");
      ensureDirectory(home);
      return home.toRealPath();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static void ensureDirectory(Path path) throws IOException {
    if (Files.isSymbolicLink(path)) {
      throw new IllegalArgumentException("workspace storage path contains a symbolic link");
    }
    Files.createDirectories(path);
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("workspace storage path is not a plain directory");
    }
  }

  private static boolean containsNul(byte[] bytes) {
    for (byte value : bytes) {
      if (value == 0) {
        return true;
      }
    }
    return false;
  }

  private static byte[] readBounded(Path file, int maxBytes, String errorMessage)
      throws IOException {
    try (InputStream input = Files.newInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
      byte[] buffer = new byte[8192];
      int total = 0;
      int count;
      while ((count = input.read(buffer, 0, Math.min(buffer.length, maxBytes - total + 1))) != -1) {
        total += count;
        if (total > maxBytes) {
          throw new IllegalArgumentException(errorMessage);
        }
        output.write(buffer, 0, count);
      }
      return output.toByteArray();
    }
  }

  private static String version(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
