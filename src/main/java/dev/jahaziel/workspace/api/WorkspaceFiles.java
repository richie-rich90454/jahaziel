/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.util.List;

/** Workspace-rooted file operations used by the browser workbench. */
public interface WorkspaceFiles {
  Operations forWorkspace(WorkspaceId workspaceId);

  /** Operations scoped to a single workspace. */
  interface Operations {
    List<Entry> list(String relativeDirectory);

    Document read(String relativePath);

    Download download(String relativePath);

    List<SearchResult> search(String query, int limit);

    Document save(String relativePath, String content, String expectedVersion, boolean createOnly);

    void createDirectories(String relativePath);

    void rename(String relativePath, String newRelativePath);

    void delete(String relativePath);
  }

  /** One visible file or directory. */
  record Entry(String name, String path, boolean directory, long size, long modifiedAt) {}

  /** UTF-8 document and its content version. */
  record Document(String path, String content, String version) {}

  /** A confined file path for a streamed attachment response. */
  record Download(Path file, String fileName, long size) {}

  /** One bounded workspace search result. Line zero means the file name matched. */
  record SearchResult(String path, int line, String preview) {}
}
