/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.cgroup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link CgroupV2} against a temporary directory. */
class CgroupV2Test {
  @TempDir private Path root;

  @Test
  void appliesLimits() throws Exception {
    UUID workspace = UUID.randomUUID();
    new CgroupV2(root).apply(workspace, 2, 2048, 4096);
    Path group = root.resolve(workspace.toString());
    assertEquals("2147483648", Files.readString(group.resolve("memory.max")));
    assertEquals("200000 100000", Files.readString(group.resolve("cpu.max")));
    assertEquals("4096", Files.readString(group.resolve("pids.max")));
  }

  @Test
  void cpuQuotaScalesWithVcpu() {
    assertEquals("100000 100000", CgroupV2.cpuMax(1));
    assertEquals("400000 100000", CgroupV2.cpuMax(4));
  }
}
