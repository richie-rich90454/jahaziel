/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only browser for SQLite files that live inside a workspace home. Callers pass a relative
 * filename, never an arbitrary filesystem path; every lookup is confined with a canonical realpath
 * check. Connections are opened read-only, statements carry a five second timeout and results are
 * capped at one thousand rows.
 */
public final class SqliteBrowser {
  public static final int MAX_ROWS = 1000;
  public static final int TIMEOUT_SECONDS = 5;

  private final Path homesRoot;

  public SqliteBrowser(Path dataRoot) {
    if (dataRoot == null) {
      throw new IllegalArgumentException("dataRoot is required");
    }
    this.homesRoot = dataRoot.resolve("workspaces");
  }

  /** Lists user tables and views in the selected database. */
  public List<String> tables(WorkspaceId workspaceId, String relativePath) {
    DataTable result =
        read(
            database(workspaceId, relativePath),
            "SELECT name FROM sqlite_master WHERE type IN ('table','view') ORDER BY name");
    List<String> names = new ArrayList<String>();
    for (List<String> row : result.rows()) {
      if (!row.isEmpty()) {
        names.add(row.get(0));
      }
    }
    return names;
  }

  /** Returns the SQLite schema rows for the selected database. */
  public DataTable schema(WorkspaceId workspaceId, String relativePath) {
    return read(
        database(workspaceId, relativePath),
        "SELECT type, name, tbl_name, sql FROM sqlite_master "
            + "WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name");
  }

  /** Returns one bounded, offset-based page of a table. */
  public DataTable rows(
      WorkspaceId workspaceId, String relativePath, String table, int offset, int limit) {
    if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("invalid table name");
    }
    if (offset < 0 || limit < 1) {
      throw new IllegalArgumentException("offset must be non-negative and limit positive");
    }
    int capped = Math.min(limit, MAX_ROWS);
    String quoted = "\"" + table + "\"";
    String sql = "SELECT * FROM " + quoted + " LIMIT " + capped + " OFFSET " + offset;
    return read(database(workspaceId, relativePath), sql);
  }

  /**
   * Runs an arbitrary read-only statement. Any write statement is rejected before it reaches the
   * driver, and the driver itself is opened in read-only mode as a second line of defence.
   */
  public DataTable query(WorkspaceId workspaceId, String relativePath, String sql) {
    if (sql == null || sql.trim().isEmpty()) {
      throw new IllegalArgumentException("sql is required");
    }
    if (isWriteStatement(sql)) {
      throw new IllegalArgumentException("only read-only statements are accepted");
    }
    return read(database(workspaceId, relativePath), sql);
  }

  private Path database(WorkspaceId workspaceId, String relativePath) {
    if (workspaceId == null) {
      throw new IllegalArgumentException("workspaceId is required");
    }
    Path home = homesRoot.resolve(workspaceId.value().toString()).resolve("home");
    Path file = PathGuard.confine(PathGuard.root(home), relativePath);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("database is not a regular file in this workspace");
    }
    return file;
  }

  private static DataTable read(Path file, String sql) {
    String url = "jdbc:sqlite:" + file.toUri().toString() + "?mode=ro";
    try (Connection connection = DriverManager.getConnection(url)) {
      try (Statement check = connection.createStatement()) {
        check.setQueryTimeout(TIMEOUT_SECONDS);
        check.execute("PRAGMA query_only=ON");
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setQueryTimeout(TIMEOUT_SECONDS);
        statement.setMaxRows(MAX_ROWS);
        try (ResultSet result = statement.executeQuery()) {
          return materialize(result);
        }
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("sqlite read failed: " + failure.getMessage(), failure);
    }
  }

  private static DataTable materialize(ResultSet result) throws SQLException {
    ResultSetMetaData meta = result.getMetaData();
    int count = meta.getColumnCount();
    List<String> columns = new ArrayList<String>();
    for (int index = 1; index <= count; index++) {
      columns.add(meta.getColumnLabel(index));
    }
    List<List<String>> rows = new ArrayList<List<String>>();
    while (result.next() && rows.size() < MAX_ROWS) {
      List<String> row = new ArrayList<String>();
      for (int index = 1; index <= count; index++) {
        Object value = result.getObject(index);
        row.add(value == null ? null : value.toString());
      }
      rows.add(row);
    }
    return new DataTable(columns, rows);
  }

  private static boolean isWriteStatement(String sql) {
    String trimmed = sql.trim();
    for (int index = 0; index < trimmed.length(); index++) {
      if (trimmed.charAt(index) == ';' && index < trimmed.length() - 1) {
        return true;
      }
    }
    String withoutTerminator =
        trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
    if (withoutTerminator.isEmpty()) {
      return true;
    }
    String first = withoutTerminator.split("\\s+")[0].toUpperCase(Locale.ROOT);
    if ("PRAGMA".equals(first) && withoutTerminator.indexOf('=') >= 0) {
      return true;
    }
    return !("SELECT".equals(first) || "WITH".equals(first) || "EXPLAIN".equals(first)
        || "PRAGMA".equals(first));
  }
}
