/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.PlatformJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Drains node spool files into the audit log. Spool files are named {@code
 * <workspace-uuid>-<date>.jsonl}; the leading 36 characters address the workspace.
 */
public final class AuditCollectorJob implements PlatformJob {
  private final AuditSpoolReader reader;
  private final Path stagingDir;

  /**
   * Creates the job.
   *
   * @param reader the spool reader
   * @param stagingDir the spool staging directory
   */
  public AuditCollectorJob(AuditSpoolReader reader, Path stagingDir) {
    this.reader = Objects.requireNonNull(reader, "reader");
    this.stagingDir = Objects.requireNonNull(stagingDir, "stagingDir");
  }

  @Override
  public void run(JobContext context) {
    try {
      Files.createDirectories(stagingDir);
    } catch (IOException failure) {
      throw new IllegalStateException("cannot create audit staging dir: " + stagingDir, failure);
    }
    for (Path spool : AuditSpoolReader.spooled(stagingDir)) {
      try {
        reader.drain(workspaceOf(spool), spool);
      } catch (IllegalArgumentException bad) {
        quarantine(spool);
      }
    }
  }

  @Override
  public Duration period() {
    return Duration.ofMinutes(1);
  }

  @Override
  public JobScope scope() {
    return JobScope.NODE;
  }

  private static UUID workspaceOf(Path spool) {
    String name = spool.getFileName().toString();
    if (name.length() < 36) {
      throw new IllegalArgumentException("spool file has no workspace prefix: " + spool);
    }
    try {
      return UUID.fromString(name.substring(0, 36));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("spool file has no workspace prefix: " + spool);
    }
  }

  private static void quarantine(Path spool) {
    Path bad = spool.resolveSibling(spool.getFileName() + ".bad");
    try {
      Files.move(spool, bad, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException failure) {
      throw new IllegalStateException("cannot quarantine spool file: " + spool, failure);
    }
  }
}
