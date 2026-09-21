/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.cgroup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** cgroup v2 enforcement for workspace VMs. Fail safe: limits apply or the VM never starts. */
public final class CgroupV2 {
  private final Path root;

  /**
   * Creates the enforcement.
   *
   * @param root the jahaziel cgroup root
   */
  public CgroupV2(Path root) {
    this.root = Objects.requireNonNull(root, "root");
  }

  /**
   * Applies limits for a workspace. I/O throttling arrives with measured device numbers; memory,
   * CPU, and process limits apply now.
   *
   * @param workspaceId the workspace identifier
   * @param vcpuCount virtual CPU count
   * @param memoryMib guest memory in MiB
   * @param pidsLimit process count limit
   */
  public void apply(UUID workspaceId, int vcpuCount, long memoryMib, int pidsLimit) {
    Path group = root.resolve(workspaceId.toString());
    try {
      Files.createDirectories(group);
      write(group.resolve("memory.max"), Long.toString(memoryMib * 1024 * 1024));
      write(group.resolve("cpu.max"), cpuMax(vcpuCount));
      write(group.resolve("pids.max"), Integer.toString(pidsLimit));
    } catch (IOException failure) {
      throw new IllegalStateException("cgroup limits failed for " + workspaceId, failure);
    }
  }

  /**
   * Computes the cpu.max content. Quota scales with vCPU count over a 100ms period.
   *
   * @param vcpuCount virtual CPU count
   * @return the cpu.max content
   */
  static String cpuMax(int vcpuCount) {
    return (vcpuCount * 100000) + " 100000";
  }

  private static void write(Path file, String content) throws IOException {
    Files.writeString(file, content);
  }
}
