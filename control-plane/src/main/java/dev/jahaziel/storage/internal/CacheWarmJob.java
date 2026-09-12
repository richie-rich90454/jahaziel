/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.storage.internal;

import dev.jahaziel.platform.api.JobContext;
import dev.jahaziel.platform.api.JobScope;
import dev.jahaziel.platform.api.PlatformJob;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Ensures the shared cache directories exist. Population itself runs from the ops cron with the
 * curated package list; this job only guarantees the layout.
 */
public final class CacheWarmJob implements PlatformJob {
  private static final List<String> LAYOUT =
      List.of("maven", "npm-ro", "cargo-registry", "go-modules", "pip-wheels");

  private final String storageRoot;

  /**
   * Creates the job.
   *
   * @param storageRoot the storage root
   */
  public CacheWarmJob(String storageRoot) {
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = storageRoot;
  }

  @Override
  public void run(JobContext context) {
    try {
      for (String tool : LAYOUT) {
        Files.createDirectories(Path.of(storageRoot, "caches", tool));
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cache layout failed under " + storageRoot, failure);
    }
  }

  @Override
  public Duration period() {
    return Duration.ofHours(24 * 7);
  }

  @Override
  public JobScope scope() {
    return JobScope.NODE;
  }
}
