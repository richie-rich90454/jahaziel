/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class SqliteBrowserTest {
  @TempDir Path temporary;

  private WorkspaceId workspaceId;
  private Path home;
  private SqliteBrowser browser;

  @BeforeEach
  void setUp() throws Exception {
    workspaceId = new WorkspaceId(UUID.randomUUID());
    home = temporary.resolve("workspaces").resolve(workspaceId.value().toString()).resolve("home");
    Files.createDirectories(home);
    browser = new SqliteBrowser(temporary);
    Connection connection = DriverManager.getConnection("jdbc:sqlite:" + home.resolve("sample.db"));
    Statement statement = connection.createStatement();
    statement.execute("CREATE TABLE people (id INTEGER PRIMARY KEY, name TEXT)");
    for (int index = 0; index < 1005; index++) {
      statement.execute("INSERT INTO people(name) VALUES ('person-" + index + "')");
    }
    statement.close();
    connection.close();
  }

  @Test
  void listsTablesAndSchema() {
    List<String> tables = browser.tables(workspaceId, "sample.db");

    assertTrue(tables.contains("people"));
    assertTrue(browser.schema(workspaceId, "sample.db").rowCount() > 0);
  }

  @Test
  void queryAndPagedRowsAreCappedAtOneThousand() {
    assertEquals(SqliteBrowser.MAX_ROWS,
        browser.query(workspaceId, "sample.db", "SELECT * FROM people").rowCount());
    assertEquals(SqliteBrowser.MAX_ROWS,
        browser.rows(workspaceId, "sample.db", "people", 0, 5000).rowCount());
    assertEquals(5, browser.rows(workspaceId, "sample.db", "people", 1000, 100).rowCount());
  }

  @Test
  void rejectsWriteStatements() throws Exception {
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        browser.query(workspaceId, "sample.db", "INSERT INTO people(name) VALUES ('intruder')");
      }
    });
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        browser.query(workspaceId, "sample.db", "SELECT 1; DROP TABLE people");
      }
    });
    Connection connection = DriverManager.getConnection("jdbc:sqlite:" + home.resolve("sample.db"));
    Statement statement = connection.createStatement();
    ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM people");
    result.next();
    assertEquals(1005, result.getInt(1));
    result.close();
    statement.close();
    connection.close();
  }

  @Test
  void rejectsTraversalAndAbsolutePaths() {
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        browser.tables(workspaceId, "../outside.db");
      }
    });
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        browser.tables(workspaceId, home.resolve("sample.db").toString());
      }
    });
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        browser.tables(workspaceId, "C:/outside.db");
      }
    });
  }

  @Test
  void neverWritesFilesOutsideTheWorkspaceHome() throws Exception {
    browser.tables(workspaceId, "sample.db");
    browser.query(workspaceId, "sample.db", "SELECT * FROM people LIMIT 1");

    assertFalse(Files.exists(temporary.resolve("outside.db")));
    assertTrue(Files.isRegularFile(home.resolve("sample.db")));
    assertEquals(1, Files.list(temporary).count());
  }
}
