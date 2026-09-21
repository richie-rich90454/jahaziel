/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.nodeagent.cgroup.CgroupV2;
import dev.jahaziel.nodeagent.config.NodeAgentConfig;
import dev.jahaziel.nodeagent.firecracker.FirecrackerClient;
import dev.jahaziel.nodeagent.jail.Jailer;
import dev.jahaziel.nodeagent.storage.CommandRunner;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.spi.ImageRef;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.ObjectMapper;

/**
 * Firecracker node backend. Implements the plugin contract against jailer, folder storage, cgroups,
 * and the Firecracker API: prepare storage and shell, boot, stop, destroy. Every VM boots the
 * shared base image read-only; workspace files live in plain host folders.
 */
public final class FirecrackerBackend implements NodeBackend {
  private final FolderStorage storage;
  private final FirecrackerClient firecracker;
  private final Jailer jailer;
  private final CgroupV2 cgroups;
  private final CommandRunner commands;
  private final NodeAgentConfig config;
  private final ConcurrentHashMap<UUID, ImageRef> images = new ConcurrentHashMap<>();
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicInteger guestCid = new AtomicInteger(3);
  private final AtomicInteger uidCounter = new AtomicInteger(100000);

  /**
   * Creates the backend.
   *
   * @param storage the folder storage
   * @param firecracker the Firecracker client
   * @param jailer the jailer
   * @param cgroups the cgroup enforcement
   * @param commands the host command runner
   * @param config the node configuration
   */
  public FirecrackerBackend(
      FolderStorage storage,
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
    images.put(workspaceId, request.image());
    cgroups.apply(workspaceId, request.vcpuCount(), request.memoryMib(), 4096);
    writeFirecrackerConfig(handle, request.image(), request.vcpuCount(), request.memoryMib());
    return handle;
  }

  @Override
  public RuntimeHandle start(RuntimeRequest request) {
    Objects.requireNonNull(request, "request");
    UUID workspaceId = request.workspaceId();
    ImageRef image = images.get(workspaceId);
    if (image == null) {
      throw new IllegalStateException("workspace not prepared: " + workspaceId);
    }
    String netns = "/var/run/netns/jahaziel-" + workspaceId;
    commands.run(List.of("ip", "netns", "add", "jahaziel-" + workspaceId));
    int uid = uidCounter.getAndIncrement();
    stageJail(workspaceId, image);
    jailer.start(workspaceId, uid, uid, netns);
    firecracker.boot(
        apiSocket(workspaceId),
        "/rootfs.img",
        "tap-vm-" + shortId(workspaceId),
        guestMac(),
        request.vcpuCount(),
        request.memoryMib(),
        "/kernel",
        request.kernelCmdlineExtra());
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
    images.remove(handle.workspaceId());
    storage.destroy(handle);
  }

  /**
   * Stages the shared base image and kernel into the jail as hard links. Same inode, zero extra
   * bytes; the guest drive stays read-only so sharing is safe.
   *
   * @param workspaceId the workspace being started
   * @param image the base image reference
   */
  private void stageJail(UUID workspaceId, ImageRef image) {
    Path jailRoot = Path.of(config.jailRoot(), workspaceId.toString(), "root");
    try {
      Files.createDirectories(jailRoot);
      linkShared(jailRoot.resolve("rootfs.img"), Path.of(imageFile(image)));
      linkShared(jailRoot.resolve("kernel"), Path.of(config.kernelImage()));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot stage jail for " + workspaceId, failure);
    }
  }

  private static void linkShared(Path link, Path target) throws IOException {
    Files.deleteIfExists(link);
    Files.createLink(link, target);
  }

  private String imageFile(ImageRef image) {
    // String concat, not Path: image refs contain colons (base-image:2026.09),
    // which are illegal in path segments on some platforms.
    return config.imagesDir() + "/" + image.value() + ".ext4";
  }

  private void writeFirecrackerConfig(
      WorkspaceHandle handle, ImageRef image, int vcpuCount, long memoryMib) {
    Map<String, Object> document =
        Map.of(
            "vcpu_count", vcpuCount,
            "memory_mib", memoryMib,
            "rootfs", imageFile(image));
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
