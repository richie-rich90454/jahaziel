/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * Samples host resources from /proc and the filesystem. Counter deltas stay in memory; the
 * collector reports them with the absolute values the control plane stores.
 */
public final class NodeMetricsCollector {
  private final Path storageRoot;
  private long lastIdle;
  private long lastTotal;
  private boolean primed;

  /**
   * Creates the collector.
   *
   * @param storageRoot the storage root whose filesystem is measured
   */
  public NodeMetricsCollector(Path storageRoot) {
    this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot");
  }

  /**
   * Samples host resources now.
   *
   * @return the sample
   */
  public synchronized HostSample sample() {
    long[] memory = readMeminfo();
    double cpu = cpuUsagePercent();
    long[] disk = readDisk();
    double[] load = readLoad();
    return new HostSample(cpu, memory[0], memory[1], disk[0], disk[1], load[0], load[1], load[2]);
  }

  private long[] readMeminfo() {
    long total = 0;
    long available = 0;
    try {
      for (String line : Files.readAllLines(Paths.get("/proc/meminfo"))) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length < 2) {
          continue;
        }
        if (fields[0].equals("MemTotal:")) {
          total = Long.parseLong(fields[1]) * 1024;
        } else if (fields[0].equals("MemAvailable:")) {
          available = Long.parseLong(fields[1]) * 1024;
        }
      }
    } catch (IOException | NumberFormatException failure) {
      throw new UncheckedIOException(new IOException("cannot read /proc/meminfo", failure));
    }
    return new long[] {Math.max(0, total - available), total};
  }

  private double cpuUsagePercent() {
    try {
      List<String> lines = Files.readAllLines(Paths.get("/proc/stat"));
      String[] fields = lines.get(0).trim().split("\\s+");
      long idle = Long.parseLong(fields[4]);
      long total = 0;
      for (int i = 1; i < fields.length; i++) {
        total += Long.parseLong(fields[i]);
      }
      double usage = 0;
      if (primed && total > lastTotal) {
        usage = 100.0 * (1.0 - (double) (idle - lastIdle) / (total - lastTotal));
      }
      lastIdle = idle;
      lastTotal = total;
      primed = true;
      return Math.max(0, Math.min(100, usage));
    } catch (IOException | NumberFormatException | IndexOutOfBoundsException failure) {
      return 0;
    }
  }

  private long[] readDisk() {
    try {
      FileStore store = Files.getFileStore(storageRoot);
      long total = store.getTotalSpace();
      return new long[] {Math.max(0, total - store.getUsableSpace()), total};
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot stat " + storageRoot, failure);
    }
  }

  private double[] readLoad() {
    OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    double one = os.getSystemLoadAverage();
    if (one < 0) {
      return new double[] {0, 0, 0};
    }
    return new double[] {one, one, one};
  }
}
