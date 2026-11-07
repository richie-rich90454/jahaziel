/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Exercises the Linux /proc branches against temporary files without touching host /proc. */
class NodeMetricsCollectorCoverageGapTest {
  @Test
  void procBranches(@TempDir Path root) throws Exception {
    Path proc = Files.createDirectory(root.resolve("proc"));
    Path meminfo = proc.resolve("meminfo");
    Path stat = proc.resolve("stat");
    Files.write(
        meminfo,
        List.of(
            "MemTotal:        8000000 kB",
            "NotAKVLine",
            "SwapTotal:           100 kB",
            "MemAvailable:    2000000 kB"));
    Files.writeString(stat, "cpu  100 200 300 400 500 0 0 0 0 0\n");
    try (MockedStatic<Paths> paths = Mockito.mockStatic(Paths.class, Mockito.CALLS_REAL_METHODS)) {
      paths.when(() -> Paths.get("/proc/meminfo")).thenReturn(meminfo);
      paths.when(() -> Paths.get("/proc/stat")).thenReturn(stat);
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

      try (MockedStatic<Files> files =
          Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
        files.when(() -> Files.readAllLines(meminfo)).thenThrow(new IOException("denied"));
        assertThrows(UncheckedIOException.class, collector::sample);
      }
    }
  }
}
