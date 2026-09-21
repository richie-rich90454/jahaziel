/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.storage.internal;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.PlatformJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/** Verifies the base image checksum against its sidecar on a schedule. Fail loud on mismatch. */
public final class ImageMaintenanceJob implements PlatformJob {
  private final Path imagesDir;
  private final String imageRef;

  /**
   * Creates the job.
   *
   * @param imagesDir the base images directory
   * @param imageRef the expected image reference, for example {@code base-image:2026.09}
   */
  public ImageMaintenanceJob(Path imagesDir, String imageRef) {
    this.imagesDir = Objects.requireNonNull(imagesDir, "imagesDir");
    if (imageRef == null || imageRef.isBlank()) {
      throw new IllegalArgumentException("imageRef must not be blank");
    }
    this.imageRef = imageRef;
  }

  @Override
  public void run(JobContext context) {
    Path artifact = imagesDir.resolve(imageRef + ".ext4.zst");
    Path sidecar = imagesDir.resolve(imageRef + ".ext4.zst.sha256");
    try {
      String expected = Files.readString(sidecar).trim().split("\\s+")[0];
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      byte[] bytes = Files.readAllBytes(artifact);
      String actual = HexFormat.of().formatHex(sha.digest(bytes));
      if (!actual.equalsIgnoreCase(expected)) {
        throw new IllegalStateException("base image checksum mismatch for " + imageRef);
      }
    } catch (IllegalStateException mismatch) {
      throw mismatch;
    } catch (Exception failure) {
      throw new IllegalStateException("base image unreadable: " + imageRef, failure);
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
