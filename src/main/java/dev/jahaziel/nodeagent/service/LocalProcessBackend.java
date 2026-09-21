/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

/**
 * Zero-install local backend. Workspaces are real folders via the injected storage; "running" means
 * the workspace is marked live and its runtime descriptor is on disk. No hypervisor, no
 * subprocesses, no extra installs: the same jar runs on Linux, Windows, Windows Server, and macOS.
 * Production Linux with Firecracker binaries uses the Firecracker backend instead.
 */
public final class LocalProcessBackend implements NodeBackend {
  private final WorkspaceStorage storage;
  private final ConcurrentHashMap<UUID, WorkspaceHandle> prepared = new ConcurrentHashMap<>();
  private final Set<UUID> running = ConcurrentHashMap.newKeySet();
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the backend.
   *
   * @param storage the folder storage with real home and jail directories
   */
  public LocalProcessBackend(WorkspaceStorage storage) {
    this.storage = Objects.requireNonNull(storage, "storage");
  }

  @Override
  public String name() {
    return "local";
  }

  @Override
  public WorkspaceHandle prepare(PrepareRequest request) {
    Objects.requireNonNull(request, "request");
    WorkspaceHandle handle =
        storage.prepare(request.workspaceId(), request.ownerId(), request.image(), request.quota());
    prepared.put(request.workspaceId(), handle);
    return handle;
  }

  @Override
  public RuntimeHandle start(RuntimeRequest request) {
    Objects.requireNonNull(request, "request");
    WorkspaceHandle handle = requirePrepared(request.workspaceId());
    running.add(request.workspaceId());
    writeRuntimeDescriptor(handle, request);
    return new RuntimeHandle(request.workspaceId());
  }

  @Override
  public void stop(RuntimeHandle handle, boolean graceful) {
    // Idempotent on purpose: a workspace that was never started must still be stoppable, so a
    // delete of a fresh workspace is not an error.
    Objects.requireNonNull(handle, "handle");
    running.remove(handle.value());
  }

  @Override
  public void destroy(WorkspaceHandle handle) {
    Objects.requireNonNull(handle, "handle");
    storage.destroy(handle);
    running.remove(handle.workspaceId());
    prepared.remove(handle.workspaceId());
  }

  /**
   * Returns whether the workspace is marked running.
   *
   * @param workspaceId the workspace
   * @return true after start until stop or destroy
   */
  public boolean isRunning(UUID workspaceId) {
    return running.contains(Objects.requireNonNull(workspaceId, "workspaceId"));
  }

  private WorkspaceHandle requirePrepared(UUID workspaceId) {
    WorkspaceHandle handle = prepared.get(Objects.requireNonNull(workspaceId, "workspaceId"));
    if (handle == null) {
      throw new IllegalStateException("workspace not prepared: " + workspaceId);
    }
    return handle;
  }

  private void writeRuntimeDescriptor(WorkspaceHandle handle, RuntimeRequest request) {
    Map<String, Object> document =
        Map.of(
            "workspace",
            handle.workspaceId().toString(),
            "vcpu_count",
            request.vcpuCount(),
            "memory_mib",
            request.memoryMib(),
            "kernel_cmdline_extra",
            List.copyOf(request.kernelCmdlineExtra()),
            "home",
            handle.homePath().toString());
    Path descriptor = handle.jailPath().resolve("runtime.json");
    try {
      Files.createDirectories(descriptor.toAbsolutePath().getParent());
      Files.writeString(descriptor, mapper.writeValueAsString(document));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot write runtime descriptor", failure);
    } catch (tools.jackson.core.JacksonException failure) {
      throw new IllegalStateException("cannot encode runtime descriptor", failure);
    }
  }
}
