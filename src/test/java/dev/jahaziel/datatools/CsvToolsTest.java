/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.api.WorkspaceFileConflictException;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.internal.WorkspaceFilesServiceImpl;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class CsvToolsTest {
  @TempDir Path temporary;

  private final CsvReader reader = new CsvReader();

  @Test
  void detectsHeaderAndInfersColumnTypes() {
    CsvTable table = reader.read("name,age,active\nAda,30,true\nBob,41,false\n");

    assertTrue(table.hasHeader());
    assertEquals(3, table.headers().size());
    assertEquals("name", table.headers().get(0));
    assertEquals("TEXT", table.types().get(0));
    assertEquals("INTEGER", table.types().get(1));
    assertEquals("BOOLEAN", table.types().get(2));
    assertEquals(2, table.rows().size());
    assertEquals("Ada", table.rows().get(0).get(0));
  }

  @Test
  void handlesFilesWithoutHeader() {
    CsvTable table = reader.read("1,2.5\n3,4.5\n");

    assertFalse(table.hasHeader());
    assertEquals("column_1", table.headers().get(0));
    assertEquals("INTEGER", table.types().get(0));
    assertEquals("REAL", table.types().get(1));
  }

  @Test
  void boundsRowsAndRoundTripsQuotedCells() {
    StringBuilder content = new StringBuilder("name,note\n");
    for (int index = 0; index < CsvReader.MAX_ROWS + 20; index++) {
      content.append("p").append(index).append(",\"a,b\"\n");
    }
    CsvTable table = reader.read(content.toString());

    assertEquals(CsvReader.MAX_ROWS, table.rows().size());
    String csv = new CsvWriter(new WorkspaceFilesServiceImpl(temporary.toString())).write(table);
    CsvTable again = reader.read(csv);
    assertEquals("a,b", again.rows().get(0).get(1));
  }

  @Test
  void saveUsesEtagStyleVersionCheck() {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    WorkspaceFiles files = new WorkspaceFilesServiceImpl(temporary.toString());
    files.forWorkspace(workspaceId).createDirectories(".");
    CsvWriter writer = new CsvWriter(files);
    CsvTable first = reader.read("name,age\nAda,30\n");

    WorkspaceFiles.Document created = writer.save(workspaceId, "people.csv", first, null);
    final String staleVersion = "not-the-current-version";
    assertThrows(WorkspaceFileConflictException.class, new Executable() {
      @Override
      public void execute() {
        writer.save(workspaceId, "people.csv", first, staleVersion);
      }
    });
    CsvTable updated = reader.read("name,age\nAda,31\n");
    WorkspaceFiles.Document saved = writer.save(
        workspaceId, "people.csv", updated, created.version());

    assertTrue(!saved.version().equals(created.version()));
    assertEquals(
        "name,age\nAda,31\n",
        files.forWorkspace(workspaceId).read("people.csv").content());
  }
}
