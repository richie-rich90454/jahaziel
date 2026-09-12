/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.internal;

import dev.jahaziel.platform.api.JobContext;
import dev.jahaziel.platform.api.JobScope;
import dev.jahaziel.platform.api.PlatformJob;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Removes orphaned jail directories: on disk but belonging to no known workspace. Home subvolumes
 * are never touched; only the throwaway jail shells go.
 */
public final class CleanupJob implements PlatformJob {
  private final WorkspaceService workspaces;
  private final String storageRoot;

  /**
   * Creates the job.
   *
   * @param workspaces the workspace lookup
   * @param storageRoot the storage root
   */
  public CleanupJob(WorkspaceService workspaces, String storageRoot) {
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = storageRoot;
  }

  @Override
  public void run(JobContext context) {
    Set<String> known =
        workspaces.describeAll().stream()
            .map(view -> view.workspace().id().value().toString())
            .collect(Collectors.toSet());
    Path jail = Path.of(storageRoot, "jail");
    if (!Files.isDirectory(jail)) {
      return;
    }
    try (Stream<Path> entries = Files.list(jail)) {
      for (Path entry : entries.toList()) {
        String name = entry.getFileName().toString();
        if (isUuid(name) && !known.contains(name)) {
          deleteTree(entry);
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cleanup failed for " + jail, failure);
    }
  }

  @Override
  public Duration period() {
    return Duration.ofHours(24);
  }

  @Override
  public JobScope scope() {
    return JobScope.NODE;
  }

  private static boolean isUuid(String name) {
    try {
      UUID.fromString(name);
      return true;
    } catch (IllegalArgumentException failure) {
      return false;
    }
  }

  private static void deleteTree(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
