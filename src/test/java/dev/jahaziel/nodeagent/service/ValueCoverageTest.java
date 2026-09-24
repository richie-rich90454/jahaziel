/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.spi.SnapshotHandle;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validation of {@link HostSample} and {@link SnapshotSummary}. */
class ValueCoverageTest {
  @Test
  void hostSampleRejectsNegatives() {
    assertThrows(IllegalArgumentException.class, () -> new HostSample(-1, 0, 0, 0, 0, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new HostSample(0, -1, 0, 0, 0, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new HostSample(0, 0, -1, 0, 0, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new HostSample(0, 0, 0, -1, 0, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new HostSample(0, 0, 0, 0, -1, 0, 0, 0));
    HostSample ok = new HostSample(10, 100, 200, 300, 400, 1, 2, 3);
    assertEquals(10, ok.cpuUsagePercent());
    assertEquals(100, ok.memoryUsedBytes());
    assertTrue(ok.toString().contains("cpuUsagePercent"));
    assertEquals(ok, new HostSample(10, 100, 200, 300, 400, 1, 2, 3));
    assertEquals(ok.hashCode(), new HostSample(10, 100, 200, 300, 400, 1, 2, 3).hashCode());
  }

  @Test
  void snapshotSummaryValidatesAndDelegates() {
    SnapshotHandle handle = new SnapshotHandle(UUID.randomUUID(), Path.of("/s"), Instant.now());
    assertThrows(NullPointerException.class, () -> new SnapshotSummary(null, "t", Instant.now()));
    assertThrows(
        IllegalArgumentException.class, () -> new SnapshotSummary(handle, null, Instant.now()));
    assertThrows(
        IllegalArgumentException.class, () -> new SnapshotSummary(handle, "  ", Instant.now()));
    assertThrows(NullPointerException.class, () -> new SnapshotSummary(handle, "t", null));
    SnapshotSummary summary = new SnapshotSummary(handle, "daily", Instant.now());
    assertEquals(handle.snapshotPath(), summary.snapshotPath());
    assertEquals(handle.snapshotId(), summary.snapshotId());
    assertEquals("daily", summary.tag());
    assertTrue(summary.toString().contains("daily"));
    assertEquals(summary, new SnapshotSummary(handle, "daily", summary.createdAt()));
  }
}
