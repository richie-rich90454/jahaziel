/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.nodeagent.audit.AuditSpool;
import dev.jahaziel.nodeagent.sync.SyncServer;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Node agent facade over the backend and storage plugins. Adds auditing, port registration, and
 * snapshot listing around the plugin calls.
 */
public final class NodeAgentService implements NodeAgentOperations {
  private final NodeBackend backend;
  private final WorkspaceStorage storage;
  private final AuditSpool spool;
  private final SyncServer sync;
  private final ConcurrentMap<String, String> ports = new ConcurrentHashMap<>();

  /**
   * Creates the facade.
   *
   * @param backend the node backend
   * @param storage the workspace storage
   * @param spool the audit spool
   * @param sync the file-sync server
   */
  public NodeAgentService(
      NodeBackend backend, WorkspaceStorage storage, AuditSpool spool, SyncServer sync) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.storage = Objects.requireNonNull(storage, "storage");
    this.spool = Objects.requireNonNull(spool, "spool");
    this.sync = Objects.requireNonNull(sync, "sync");
  }

  @Override
  public WorkspaceHandle prepareWorkspace(
      UUID workspaceId,
      UUID ownerId,
      String imageRef,
      long memoryMib,
      int vcpuCount,
      dev.jahaziel.spi.QuotaBytes quota) {
    WorkspaceHandle handle =
        backend.prepare(
            new PrepareRequest(
                workspaceId,
                ownerId,
                new dev.jahaziel.spi.ImageRef(imageRef),
                quota,
                vcpuCount,
                memoryMib));
    sync.register(handle.workspaceId(), handle.homePath(), quota.value());
    audit(workspaceId, "workspace.prepare", Map.of("image", imageRef));
    return handle;
  }

  @Override
  public void startRuntime(WorkspaceHandle handle, RuntimeRequest config) {
    Objects.requireNonNull(handle, "handle");
    backend.start(withSyncArgs(handle, config));
    audit(handle.workspaceId(), "runtime.start", Map.of());
  }

  @Override
  public void stopRuntime(WorkspaceHandle handle, boolean graceful, int timeoutSeconds) {
    Objects.requireNonNull(handle, "handle");
    backend.stop(new RuntimeHandle(handle.workspaceId()), graceful);
    audit(handle.workspaceId(), "runtime.stop", Map.of("graceful", Boolean.toString(graceful)));
  }

  @Override
  public void destroyWorkspace(WorkspaceHandle handle, boolean keepSnapshots) {
    Objects.requireNonNull(handle, "handle");
    backend.destroy(handle);
    sync.deregister(handle.workspaceId());
    audit(handle.workspaceId(), "workspace.destroy", Map.of());
  }

  @Override
  public SnapshotHandle createSnapshot(WorkspaceHandle handle, SnapshotTag tag, String createdBy) {
    SnapshotHandle snapshot = storage.snapshot(handle, tag);
    audit(
        handle.workspaceId(),
        "snapshot.create",
        Map.of("tag", tag.value(), "created_by", createdBy));
    return snapshot;
  }

  @Override
  public void restoreSnapshot(WorkspaceHandle handle, SnapshotHandle snapshot) {
    storage.restore(handle, snapshot);
    audit(handle.workspaceId(), "snapshot.restore", Map.of());
  }

  @Override
  public void deleteSnapshot(SnapshotHandle snapshot) {
    // Audited by the control plane caller, which owns the workspace context.
    storage.deleteSnapshot(snapshot);
  }

  @Override
  public List<SnapshotSummary> listSnapshots(WorkspaceHandle handle) {
    Objects.requireNonNull(handle, "handle");
    Path snaps = handle.homePath().getParent().resolve("snaps");
    try (Stream<Path> entries = Files.list(snaps)) {
      return entries
          .map(
              path ->
                  new SnapshotSummary(
                      new SnapshotHandle(UUID.randomUUID(), path, Instant.now()),
                      path.getFileName().toString(),
                      Instant.now()))
          .toList();
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot list snapshots for " + handle.workspaceId(), failure);
    }
  }

  @Override
  public String attachPort(WorkspaceHandle handle, int port, String protocol) {
    Objects.requireNonNull(handle, "handle");
    String vsock = "vsock:" + handle.workspaceId() + ":" + port;
    ports.put(handle.workspaceId() + ":" + port, vsock);
    audit(handle.workspaceId(), "port.attach", Map.of("port", Integer.toString(port)));
    return vsock;
  }

  @Override
  public void detachPort(WorkspaceHandle handle, int port) {
    Objects.requireNonNull(handle, "handle");
    ports.remove(handle.workspaceId() + ":" + port);
    audit(handle.workspaceId(), "port.detach", Map.of("port", Integer.toString(port)));
  }

  /**
   * Copies a runtime request, appending the file-sync kernel arguments that hand the guest its
   * workspace id and sync secret.
   *
   * @param handle the storage handle
   * @param config the runtime configuration
   * @return the configuration with sync arguments
   */
  private RuntimeRequest withSyncArgs(WorkspaceHandle handle, RuntimeRequest config) {
    Objects.requireNonNull(config, "config");
    List<String> extra = new ArrayList<>(config.kernelCmdlineExtra());
    extra.add("jahaziel.workspace=" + handle.workspaceId());
    extra.add("jahaziel.sync-secret=" + sync.secretFor(handle.workspaceId()));
    return new RuntimeRequest(
        config.workspaceId(),
        config.vcpuCount(),
        config.memoryMib(),
        config.diskIopsLimit(),
        config.diskBandwidthBytesPerSec(),
        config.networkBandwidthBytesPerSec(),
        config.networkConnectionsLimit(),
        config.pidsLimit(),
        List.copyOf(extra));
  }

  private void audit(UUID workspaceId, String action, Map<String, String> details) {
    StringBuilder json = new StringBuilder("{\"action\":\"").append(action).append('"');
    details.forEach(
        (key, value) -> json.append(",\"").append(key).append("\":\"").append(value).append('"'));
    json.append('}');
    spool.append(workspaceId, json.toString());
  }
}
