/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.nodeagent.cgroup.CgroupV2;
import dev.jahaziel.nodeagent.config.NodeAgentConfig;
import dev.jahaziel.nodeagent.firecracker.FirecrackerClient;
import dev.jahaziel.nodeagent.jail.Jailer;
import dev.jahaziel.nodeagent.storage.BtrfsStorage;
import dev.jahaziel.nodeagent.storage.CommandRunner;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.ObjectMapper;

/**
 * Firecracker node backend. Implements the plugin contract against jailer, Btrfs, cgroups, and
 * the Firecracker API: prepare storage and shell, boot, stop, destroy.
 */
public final class FirecrackerBackend implements NodeBackend {
  private final BtrfsStorage storage;
  private final FirecrackerClient firecracker;
  private final Jailer jailer;
  private final CgroupV2 cgroups;
  private final CommandRunner commands;
  private final NodeAgentConfig config;
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicInteger guestCid = new AtomicInteger(3);
  private final AtomicInteger uidCounter = new AtomicInteger(100000);

  /**
   * Creates the backend.
   *
   * @param storage the Btrfs storage
   * @param firecracker the Firecracker client
   * @param jailer the jailer
   * @param cgroups the cgroup enforcement
   * @param commands the host command runner
   * @param config the node configuration
   */
  public FirecrackerBackend(
      BtrfsStorage storage,
      FirecrackerClient firecracker,
      Jailer jailer,
      CgroupV2 cgroups,
      CommandRunner commands,
      NodeAgentConfig config) {
    this.storage = Objects.requireNonNull(storage, "storage");
    this.firecracker = Objects.requireNonNull(firecracker, "firecracker");
    this.jailer = Objects.requireNonNull(jailer, "jailer");
    this.cgroups = Objects.requireNonNull(cgroups, "cgroups");
    this.commands = Objects.requireNonNull(commands, "commands");
    this.config = Objects.requireNonNull(config, "config");
  }

  @Override
  public String name() {
    return "firecracker";
  }

  @Override
  public WorkspaceHandle prepare(PrepareRequest request) {
    Objects.requireNonNull(request, "request");
    UUID workspaceId = request.workspaceId();
    WorkspaceHandle handle =
        storage.prepare(workspaceId, request.ownerId(), request.image(), request.quota());
    cgroups.apply(workspaceId, request.vcpuCount(), request.memoryMib(), 4096);
    writeFirecrackerConfig(handle, request.vcpuCount(), request.memoryMib());
    return handle;
  }

  @Override
  public RuntimeHandle start(RuntimeRequest request) {
    Objects.requireNonNull(request, "request");
    UUID workspaceId = request.workspaceId();
    String netns = "/var/run/netns/jahaziel-" + workspaceId;
    commands.run(List.of("ip", "netns", "add", "jahaziel-" + workspaceId));
    int uid = uidCounter.getAndIncrement();
    jailer.start(workspaceId, uid, uid, netns);
    firecracker.boot(
        apiSocket(workspaceId),
        "/rootfs.img",
        "tap-vm-" + shortId(workspaceId),
        guestMac(),
        request.vcpuCount(),
        request.memoryMib(),
        "/kernel");
    return new RuntimeHandle(workspaceId);
  }

  @Override
  public void stop(RuntimeHandle handle, boolean graceful) {
    Objects.requireNonNull(handle, "handle");
    if (graceful) {
      try {
        firecracker.shutdown(apiSocket(handle.value()));
      } catch (RuntimeException unreachable) {
        // Already gone; the stop still counts.
      }
    }
  }

  @Override
  public void destroy(WorkspaceHandle handle) {
    Objects.requireNonNull(handle, "handle");
    commands.run(List.of("ip", "netns", "delete", "jahaziel-" + handle.workspaceId()));
    storage.destroy(handle);
  }

  private void writeFirecrackerConfig(WorkspaceHandle handle, int vcpuCount, long memoryMib) {
    Map<String, Object> document =
        Map.of(
            "vcpu_count", vcpuCount,
            "memory_mib", memoryMib,
            "rootfs", handle.rootDiskPath().toString());
    Path configPath = handle.jailPath().resolve("root").resolve("firecracker.json");
    try {
      Files.createDirectories(configPath.getParent());
      Files.writeString(configPath, mapper.writeValueAsString(document));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot write firecracker config", failure);
    } catch (tools.jackson.core.JacksonException failure) {
      throw new IllegalStateException("cannot encode firecracker config", failure);
    }
  }

  private String apiSocket(UUID workspaceId) {
    return config.jailRoot() + "/" + workspaceId + "/root/api.sock";
  }

  private String guestMac() {
    int cid = guestCid.getAndIncrement();
    return String.format("AA:FC:00:00:%02X:%02X", (cid >> 8) & 0xFF, cid & 0xFF);
  }

  private static String shortId(UUID workspaceId) {
    return workspaceId.toString().substring(0, 8);
  }
}
