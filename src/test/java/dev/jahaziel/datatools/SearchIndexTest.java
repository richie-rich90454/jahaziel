/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchIndexTest {
  @TempDir Path temporary;

  private WorkspaceId workspaceId;
  private Path home;
  private SearchIndex index;

  @BeforeEach
  void setUp() throws Exception {
    workspaceId = new WorkspaceId(UUID.randomUUID());
    home = temporary.resolve("workspaces").resolve(workspaceId.value().toString()).resolve("home");
    Files.createDirectories(home.resolve("node_modules"));
    Files.write(home.resolve("a.txt"), "hello world alpha".getBytes(StandardCharsets.UTF_8));
    Files.write(home.resolve("notes.md"), "beta search term".getBytes(StandardCharsets.UTF_8));
    Files.write(
        home.resolve("node_modules").resolve("lib.js"), "needle".getBytes(StandardCharsets.UTF_8));
    Files.write(home.resolve("skip.log"), "needle".getBytes(StandardCharsets.UTF_8));
    index = new SearchIndex(temporary, Arrays.asList("node_modules", "*.log"));
  }

  @Test
  void indexesIncrementallyByMtimeAndSize() throws Exception {
    assertEquals(2, index.index(workspaceId));
    assertEquals(0, index.index(workspaceId));

    Path changed = home.resolve("a.txt");
    Files.write(changed, "hello world alpha with additional words".getBytes(StandardCharsets.UTF_8));
    Files.setLastModifiedTime(changed, FileTime.fromMillis(System.currentTimeMillis() + 10000L));

    assertEquals(1, index.index(workspaceId));
  }

  @Test
  void searchRespectsIgnorePatternsAndRebuildRestoresIndex() throws Exception {
    index.index(workspaceId);

    List<IndexHit> alpha = index.search(workspaceId, "alpha", 10);
    assertEquals(1, alpha.size());
    assertEquals("a.txt", alpha.get(0).path());
    assertEquals(0, index.search(workspaceId, "needle", 10).size());

    Files.delete(index.database(workspaceId));
    assertEquals(2, index.rebuild(workspaceId));
    assertEquals(1, index.search(workspaceId, "beta", 10).size());
  }

  @Test
  void indexDatabaseStaysOutsideWorkspaceHome() throws Exception {
    index.index(workspaceId);

    Path database = index.database(workspaceId);
    assertTrue(Files.isRegularFile(database));
    assertTrue(database.startsWith(temporary.resolve("index")));
    assertFalse(database.startsWith(home));
    assertTrue(Files.isDirectory(temporary.resolve("index")));
    assertFalse(Files.exists(home.resolve(workspaceId.value().toString() + ".db")));
  }
}
