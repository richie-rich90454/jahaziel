/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory node agent for development and tests. Implements the operations facade and both
 * plugin contracts with no host calls and instant transitions.
 */
public final class FakeNodeAgent implements NodeAgentOperations, NodeBackend, WorkspaceStorage {
  private final ConcurrentMap<UUID, WorkspaceHandle> prepared = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, List<SnapshotSummary>> snapshots = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> ports = new ConcurrentHashMap<>();

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public WorkspaceHandle prepare(PrepareRequest request) {
    Objects.requireNonNull(request, "request");
    return prepareWorkspace(
        request.workspaceId(),
        request.ownerId(),
        request.image().value(),
        0,
        request.memoryMib(),
        request.vcpuCount(),
        request.quota());
  }

  @Override
  public WorkspaceHandle prepare(UUID workspaceId, UUID ownerId, ImageRef image, QuotaBytes quota) {
    return prepareWorkspace(workspaceId, ownerId, image.value(), 0, 0, 0, quota);
  }

  @Override
  public RuntimeHandle start(RuntimeRequest request) {
    Objects.requireNonNull(request, "request");
    requirePrepared(request.workspaceId());
    return new RuntimeHandle(request.workspaceId());
  }

  @Override
  public void stop(RuntimeHandle handle, boolean graceful) {
    Objects.requireNonNull(handle, "handle");
    requirePrepared(handle.value());
  }

  @Override
  public void destroy(WorkspaceHandle handle) {
    requirePrepared(handle);
    prepared.remove(handle.workspaceId());
  }

  @Override
  public SnapshotHandle snapshot(WorkspaceHandle handle, SnapshotTag tag) {
    return createSnapshot(handle, tag, "fake");
  }

  @Override
  public void deleteSnapshot(SnapshotHandle snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    snapshots
        .values()
        .forEach(
            list ->
                list.removeIf(summary -> summary.snapshotId().equals(snapshot.snapshotId())));
  }

  @Override
  public void restore(WorkspaceHandle handle, SnapshotHandle snapshot) {
    restoreSnapshot(handle, snapshot);
  }

  @Override
  public StorageUsage usage(WorkspaceHandle handle) {
    requirePrepared(handle);
    return new StorageUsage(0, 0, 0, 0);
  }

  @Override
  public WorkspaceHandle prepareWorkspace(
      UUID workspaceId,
      UUID ownerId,
      String imageRef,
      long diskSizeBytes,
      long memoryMib,
      int vcpuCount,
      QuotaBytes quota) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    WorkspaceHandle handle =
        new WorkspaceHandle(
            workspaceId,
            Path.of("/jail/" + workspaceId + "/rootfs.img"),
            Path.of("/workspaces/" + ownerId + "/home"),
            Path.of("/jail/" + workspaceId));
    prepared.put(workspaceId, handle);
    return handle;
  }

  @Override
  public void startRuntime(WorkspaceHandle handle, RuntimeRequest config) {
    requirePrepared(handle);
  }

  @Override
  public void stopRuntime(WorkspaceHandle handle, boolean graceful, int timeoutSeconds) {
    requirePrepared(handle);
  }

  @Override
  public void destroyWorkspace(WorkspaceHandle handle, boolean keepSnapshots) {
    requirePrepared(handle);
    prepared.remove(handle.workspaceId());
    if (!keepSnapshots) {
      snapshots.remove(handle.workspaceId());
    }
  }

  @Override
  public SnapshotHandle createSnapshot(WorkspaceHandle handle, SnapshotTag tag, String createdBy) {
    requirePrepared(handle);
    SnapshotHandle snapshot =
        new SnapshotHandle(
            UUID.randomUUID(),
            Path.of("/workspaces/" + handle.workspaceId() + "/snaps/" + tag.value()),
            Instant.now());
    snapshots
        .computeIfAbsent(handle.workspaceId(), key -> new ArrayList<>())
        .add(new SnapshotSummary(snapshot, tag.value(), snapshot.createdAt()));
    return snapshot;
  }

  @Override
  public void restoreSnapshot(WorkspaceHandle handle, SnapshotHandle snapshot) {
    requirePrepared(handle);
  }

  @Override
  public List<SnapshotSummary> listSnapshots(WorkspaceHandle handle) {
    requirePrepared(handle);
    return List.copyOf(snapshots.getOrDefault(handle.workspaceId(), List.of()));
  }

  @Override
  public String attachPort(WorkspaceHandle handle, int port, String protocol) {
    requirePrepared(handle);
    String vsock = "vsock:" + handle.workspaceId() + ":" + port;
    ports.put(handle.workspaceId() + ":" + port, vsock);
    return vsock;
  }

  @Override
  public void detachPort(WorkspaceHandle handle, int port) {
    requirePrepared(handle);
    ports.remove(handle.workspaceId() + ":" + port);
  }

  /** Returns whether a workspace is prepared. */
  public boolean isPrepared(UUID workspaceId) {
    return prepared.containsKey(workspaceId);
  }

  private void requirePrepared(WorkspaceHandle handle) {
    Objects.requireNonNull(handle, "handle");
    requirePrepared(handle.workspaceId());
  }

  private void requirePrepared(UUID workspaceId) {
    if (!prepared.containsKey(workspaceId)) {
      throw new IllegalArgumentException("workspace not prepared: " + workspaceId);
    }
  }
}
