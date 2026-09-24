/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The /proc branches of {@link NodeMetricsCollector} for machines with a writable drive root. */
class NodeMetricsCollectorCoverageGapTest {
  @Test
  void procBranches(@TempDir Path root) throws Exception {
    // ponytail: Paths.get("/proc/meminfo") resolves on the drive root; skip when locked down.
    Path driveProc = Paths.get("/proc/meminfo").toAbsolutePath().getRoot().resolve("proc");
    boolean writable;
    try {
      Files.createDirectories(driveProc);
      writable = true;
    } catch (IOException | SecurityException ignored) {
      writable = false;
    }
    assumeTrue(writable, "drive root is not writable; /proc fixtures unavailable");
    Path meminfo = driveProc.resolve("meminfo");
    Path stat = driveProc.resolve("stat");
    try {
      Files.write(
          meminfo,
          List.of(
              "MemTotal:        8000000 kB",
              "NotAKVLine",
              "SwapTotal:           100 kB",
              "MemAvailable:    2000000 kB"));
      Files.writeString(stat, "cpu  100 200 300 400 500 0 0 0 0 0\n");
      NodeMetricsCollector collector = new NodeMetricsCollector(root);
      HostSample first = collector.sample();
      assertEquals(6000000L * 1024, first.memoryUsedBytes());
      assertEquals(8000000L * 1024, first.memoryTotalBytes());
      assertEquals(0, first.cpuUsagePercent());
      HostSample second = collector.sample();
      assertEquals(0, second.cpuUsagePercent());
      Files.writeString(stat, "cpu  200 400 600 900 1000 0 0 0 0 0\n");
      HostSample third = collector.sample();
      assertEquals(68.75, third.cpuUsagePercent(), 1e-9);

      AclFileAttributeView view = Files.getFileAttributeView(meminfo, AclFileAttributeView.class);
      if (view != null) {
        List<AclEntry> original = new ArrayList<>(view.getAcl());
        List<AclEntry> updated = new ArrayList<>(original);
        updated.add(
            0,
            AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(Files.getOwner(meminfo))
                .setPermissions(AclEntryPermission.READ_DATA)
                .build());
        view.setAcl(updated);
        try {
          assertThrows(UncheckedIOException.class, collector::sample);
        } finally {
          try {
            view.setAcl(original);
          } catch (Exception ignored) {
            // Best effort; the fixture is deleted below.
          }
        }
      }
    } finally {
      Files.deleteIfExists(meminfo);
      Files.deleteIfExists(stat);
      try {
        Files.deleteIfExists(driveProc);
      } catch (Exception ignored) {
        // Best effort; an empty stray directory is harmless.
      }
    }
  }
}
