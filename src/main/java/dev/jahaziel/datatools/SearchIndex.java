/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-workspace SQLite FTS5 index. The database lives under the jar data directory's {@code index}
 * folder, outside every workspace home, so snapshots never include it. Files are reindexed only
 * when their modified time or size changes.
 */
public final class SearchIndex {
  public static final int MAX_RESULTS = 100;
  private static final long MAX_FILE_BYTES = 1024 * 1024;

  private final Path indexRoot;
  private final Path homesRoot;
  private final List<String> ignorePatterns;

  public SearchIndex(Path dataRoot, List<String> ignorePatterns) {
    if (dataRoot == null) {
      throw new IllegalArgumentException("dataRoot is required");
    }
    this.indexRoot = dataRoot.resolve("index");
    this.homesRoot = dataRoot.resolve("workspaces");
    this.ignorePatterns = new ArrayList<String>();
    if (ignorePatterns != null) {
      this.ignorePatterns.addAll(ignorePatterns);
    }
  }

  /** Incrementally indexes files whose mtime or size changed. Returns the number updated. */
  public int index(WorkspaceId workspaceId) {
    return synchronize(workspaceId, false);
  }

  /** Drops and rebuilds the whole index. Returns the number of files indexed. */
  public int rebuild(WorkspaceId workspaceId) {
    return synchronize(workspaceId, true);
  }

  /** Searches the index for a bounded set of matches. */
  public List<IndexHit> search(WorkspaceId workspaceId, String query, int limit) {
    if (query == null || query.trim().isEmpty()) {
      throw new IllegalArgumentException("query is required");
    }
    if (limit < 1 || limit > MAX_RESULTS) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_RESULTS);
    }
    Path database = database(workspaceId);
    if (!Files.isRegularFile(database)) {
      return new ArrayList<IndexHit>();
    }
    String phrase = "\"" + query.replace("\"", "\"\"") + "\"";
    List<IndexHit> hits = new ArrayList<IndexHit>();
    String sql =
        "SELECT path, snippet(fts, 0, '[', ']', ' ', 8) AS excerpt "
            + "FROM fts WHERE fts MATCH ? LIMIT ?";
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, phrase);
        statement.setInt(2, limit);
        try (ResultSet result = statement.executeQuery()) {
          while (result.next()) {
            hits.add(new IndexHit(result.getString(1), result.getString(2)));
          }
        }
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("search failed: " + failure.getMessage(), failure);
    }
    return hits;
  }

  /** Returns the index database location for a workspace. */
  public Path database(WorkspaceId workspaceId) {
    if (workspaceId == null) {
      throw new IllegalArgumentException("workspaceId is required");
    }
    return indexRoot.resolve(workspaceId.value().toString() + ".db");
  }

  private int synchronize(WorkspaceId workspaceId, boolean rebuild) {
    Path home = homesRoot.resolve(workspaceId.value().toString()).resolve("home");
    if (!Files.isDirectory(home)) {
      return 0;
    }
    Path database = database(workspaceId);
    try {
      Files.createDirectories(indexRoot);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot prepare index directory", failure);
    }
    int changed = 0;
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      initialize(connection, rebuild);
      List<Path> files = collect(home);
      for (Path file : files) {
        String relative = home.relativize(file).toString().replace('\\', '/');
        if (isIgnored(relative)) {
          continue;
        }
        BasicFileAttributes attributes;
        try {
          attributes = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException unreadable) {
          continue;
        }
        if (attributes.size() > MAX_FILE_BYTES) {
          continue;
        }
        long mtime = attributes.lastModifiedTime().toMillis();
        long size = attributes.size();
        try {
          if (!unchanged(connection, relative, mtime, size)) {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            upsert(connection, relative, mtime, size, content);
            changed++;
          }
        } catch (IOException unreadable) {
          continue;
        }
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("index update failed: " + failure.getMessage(), failure);
    }
    return changed;
  }

  private static void initialize(Connection connection, boolean rebuild) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE IF NOT EXISTS files ("
              + "path TEXT PRIMARY KEY, mtime INTEGER NOT NULL, size INTEGER NOT NULL)");
      if (rebuild) {
        statement.execute("DELETE FROM files");
        statement.execute("DROP TABLE IF EXISTS fts");
      }
      statement.execute(
          "CREATE VIRTUAL TABLE IF NOT EXISTS fts USING fts5(content, path UNINDEXED)");
      if (rebuild) {
        statement.execute("DELETE FROM fts");
      }
    }
  }

  private static boolean unchanged(Connection connection, String path, long mtime, long size)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT mtime, size FROM files WHERE path = ?")) {
      statement.setString(1, path);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return false;
        }
        return result.getLong(1) == mtime && result.getLong(2) == size;
      }
    }
  }

  private static void upsert(
      Connection connection, String path, long mtime, long size, String content)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT OR REPLACE INTO files(path, mtime, size) VALUES (?, ?, ?)")) {
      statement.setString(1, path);
      statement.setLong(2, mtime);
      statement.setLong(3, size);
      statement.executeUpdate();
    }
    try (PreparedStatement statement = connection.prepareStatement(
        "DELETE FROM fts WHERE path = ?")) {
      statement.setString(1, path);
      statement.executeUpdate();
    }
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO fts(content, path) VALUES (?, ?)")) {
      statement.setString(1, content);
      statement.setString(2, path);
      statement.executeUpdate();
    }
  }

  private List<Path> collect(Path home) {
    final List<Path> files = new ArrayList<Path>();
    try {
      Files.walkFileTree(home, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(
            Path directory, BasicFileAttributes attributes) {
          if (isIgnoredName(directory.getFileName())) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
          if (attributes.isRegularFile()) {
            files.add(file);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot walk workspace home", failure);
    }
    return files;
  }

  private boolean isIgnored(String relative) {
    String[] segments = relative.split("/");
    for (String segment : segments) {
      if (isIgnoredName(segment)) {
        return true;
      }
    }
    return false;
  }

  private boolean isIgnoredName(Path name) {
    return name != null && isIgnoredName(name.toString());
  }

  private boolean isIgnoredName(String name) {
    for (String pattern : ignorePatterns) {
      String trimmed = pattern.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.startsWith("*.") && name.endsWith(trimmed.substring(1))) {
        return true;
      }
      if (trimmed.equals(name)) {
        return true;
      }
    }
    return false;
  }
}
