/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.jail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Jailer invocation and jail directories. Every workspace VM gets its own jail owned by a
 * per-VM UID.
 */
public final class Jailer {
  private final String jailerBinary;
  private final String firecrackerBinary;
  private final String jailRoot;

  /**
   * Creates the jailer.
   *
   * @param jailerBinary the jailer binary path
   * @param firecrackerBinary the firecracker binary path
   * @param jailRoot the jail base directory
   */
  public Jailer(String jailerBinary, String firecrackerBinary, String jailRoot) {
    if (jailerBinary == null || jailerBinary.isBlank()) {
      throw new IllegalArgumentException("jailerBinary must not be blank");
    }
    if (firecrackerBinary == null || firecrackerBinary.isBlank()) {
      throw new IllegalArgumentException("firecrackerBinary must not be blank");
    }
    if (jailRoot == null || jailRoot.isBlank()) {
      throw new IllegalArgumentException("jailRoot must not be blank");
    }
    this.jailerBinary = jailerBinary;
    this.firecrackerBinary = firecrackerBinary;
    this.jailRoot = jailRoot;
  }

  /**
   * Builds the jailer command for a workspace.
   *
   * @param workspaceId the workspace identifier
   * @param uid the per-VM UID
   * @param gid the per-VM GID
   * @param netnsPath the network namespace path
   * @return the argv
   */
  public List<String> jailCommand(UUID workspaceId, int uid, int gid, String netnsPath) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(netnsPath, "netnsPath");
    List<String> command = new ArrayList<>();
    command.add(jailerBinary);
    command.add("--id");
    command.add(workspaceId.toString());
    command.add("--exec-file");
    command.add(firecrackerBinary);
    command.add("--uid");
    command.add(Integer.toString(uid));
    command.add("--gid");
    command.add(Integer.toString(gid));
    command.add("--chroot-base-dir");
    command.add(jailRoot);
    command.add("--netns");
    command.add(netnsPath);
    command.add("--cgroup-version");
    command.add("2");
    command.add("--parent-cgroup");
    command.add("/sys/fs/cgroup/jahaziel");
    command.add("--resource-limits");
    command.add("no-file=4096:8192");
    command.add("--");
    command.add("--api-sock");
    command.add("/run/firecracker.sock");
    command.add("--config-file");
    command.add("/run/firecracker.json");
    return List.copyOf(command);
  }

  /**
   * Starts a jailed Firecracker process.
   *
   * @param workspaceId the workspace identifier
   * @param uid the per-VM UID
   * @param gid the per-VM GID
   * @param netnsPath the network namespace path
   * @return the process handle
   */
  public Process start(UUID workspaceId, int uid, int gid, String netnsPath) {
    try {
      return new ProcessBuilder(jailCommand(workspaceId, uid, gid, netnsPath))
          .redirectErrorStream(true)
          .start();
    } catch (IOException failure) {
      throw new IllegalStateException("jailer failed to start for " + workspaceId, failure);
    }
  }
}
