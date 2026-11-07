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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trusted single-user backend. A workspace is a real per-workspace home directory. Starting it
 * records lifecycle state only; processes are created only for an explicit terminal or run task.
 * Processes share the Jahaziel service account and are not a hostile multi-tenant boundary.
 */
public final class LocalProcessBackend implements NodeBackend {
  private final WorkspaceStorage storage;
  private final ConcurrentHashMap<UUID, WorkspaceHandle> prepared = new ConcurrentHashMap<>();
  private final Set<UUID> running = ConcurrentHashMap.newKeySet();

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
    destroy(handle, false);
  }

  @Override
  public void destroy(WorkspaceHandle handle, boolean keepSnapshots) {
    Objects.requireNonNull(handle, "handle");
    storage.destroy(handle, keepSnapshots);
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
}
