/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves per-workspace home paths without following symbolic-link escapes. */
public final class WorkspaceDirectoryServiceImpl implements WorkspaceDirectoryService {
  private final Path storageRoot;

  public WorkspaceDirectoryServiceImpl(String storageRoot) {
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public Path home(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Path data = storageRoot;
    Path workspaceRoot = data.resolve("workspaces");
    Path workspace = workspaceRoot.resolve(workspaceId.value().toString());
    Path home = workspace.resolve("home");
    try {
      ensureDirectory(data);
      ensureDirectory(workspaceRoot);
      ensureDirectory(workspace);
      ensureDirectory(home);
      Path realData = data.toRealPath();
      Path realHome = home.toRealPath();
      if (!realHome.startsWith(realData)) {
        throw new IOException("workspace home escapes application data");
      }
      return realHome;
    } catch (IOException failure) {
      throw new UncheckedIOException("workspace home is unavailable", failure);
    }
  }

  private static void ensureDirectory(Path path) throws IOException {
    if (Files.isSymbolicLink(path)) {
      throw new IOException("workspace storage path is a symbolic link");
    }
    Files.createDirectories(path);
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("workspace storage path is not a plain directory");
    }
  }
}
