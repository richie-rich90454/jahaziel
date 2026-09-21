/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.storage.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.spi.JobContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link ImageMaintenanceJob} against a temporary directory. */
class ImageMaintenanceJobTest {
  @TempDir private Path images;

  @Test
  void acceptsMatchingChecksum() throws Exception {
    Files.write(images.resolve("base-image-2026.09.ext4.zst"), new byte[] {1, 2, 3});
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    String hex = HexFormat.of().formatHex(sha.digest(new byte[] {1, 2, 3}));
    Files.writeString(images.resolve("base-image-2026.09.ext4.zst.sha256"), hex + "  file\n");
    ImageMaintenanceJob job = new ImageMaintenanceJob(images, "base-image-2026.09");
    assertDoesNotThrow(() -> job.run(new JobContext(Instant.now())));
  }

  @Test
  void rejectsMismatchAndMissing() {
    assertThrows(
        IllegalStateException.class,
        () -> new ImageMaintenanceJob(images, "missing").run(new JobContext(Instant.now())));
  }
}
