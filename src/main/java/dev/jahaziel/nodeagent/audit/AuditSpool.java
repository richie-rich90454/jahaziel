/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Audit spool for the control plane collector. Guest events land as JSON lines; the collector
 * drains finished day files.
 */
public final class AuditSpool {
  private final Path stagingDir;

  /**
   * Creates the spool.
   *
   * @param stagingDir the audit staging directory
   */
  public AuditSpool(Path stagingDir) {
    this.stagingDir = Objects.requireNonNull(stagingDir, "stagingDir");
  }

  /**
   * Appends one JSON event line for a workspace.
   *
   * @param workspaceId the workspace identifier
   * @param jsonLine the JSON event line without trailing newline
   */
  public void append(UUID workspaceId, String jsonLine) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (jsonLine == null || jsonLine.isBlank()) {
      throw new IllegalArgumentException("jsonLine must not be blank");
    }
    Path file = stagingDir.resolve(workspaceId + "-" + LocalDate.now() + ".jsonl");
    try {
      Files.createDirectories(stagingDir);
      Files.writeString(
          file, jsonLine + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException failure) {
      throw new UncheckedIOException("audit spool failed for " + workspaceId, failure);
    }
  }

  /**
   * Lists spooled files.
   *
   * @return the spooled file paths, empty before the first append
   */
  public List<Path> spooled() {
    if (!Files.isDirectory(stagingDir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(stagingDir)) {
      return files.filter(path -> path.toString().endsWith(".jsonl")).toList();
    } catch (IOException failure) {
      throw new UncheckedIOException("audit spool unreadable at " + stagingDir, failure);
    }
  }
}
