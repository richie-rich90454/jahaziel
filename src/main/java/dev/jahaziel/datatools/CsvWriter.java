/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;

/** Writes CSV tables back through the workspace files API with ETag-style version checks. */
public final class CsvWriter {
  private final WorkspaceFiles files;

  public CsvWriter(WorkspaceFiles files) {
    if (files == null) {
      throw new IllegalArgumentException("files are required");
    }
    this.files = files;
  }

  /** Renders a table to CSV text without touching the filesystem. */
  public String write(CsvTable table) {
    if (table == null) {
      throw new IllegalArgumentException("table is required");
    }
    return table.toCsv();
  }

  /**
   * Saves a table to a workspace file. Supplying the current version enforces an optimistic
   * conflict check; a null version creates the file only if it does not already exist.
   */
  public WorkspaceFiles.Document save(
      WorkspaceId workspaceId, String relativePath, CsvTable table, String expectedVersion) {
    WorkspaceFiles.Operations operations = files.forWorkspace(workspaceId);
    return operations.save(relativePath, write(table), expectedVersion, expectedVersion == null);
  }
}
