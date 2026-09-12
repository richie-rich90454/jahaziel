/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import java.util.List;
import java.util.UUID;

/**
 * Node agent operations. Implemented once against real subsystems and once in memory for
 * development and tests.
 */
public interface NodeAgentOperations {
  /**
   * Prepares storage and runtime shell for a workspace.
   *
   * @param workspaceId the workspace identifier
   * @param ownerId the owning user identifier
   * @param imageRef the base image reference
   * @param diskSizeBytes the root disk size in bytes
   * @param memoryMib guest memory in MiB
   * @param vcpuCount virtual CPU count
   * @param quota the home quota
   * @return the storage handle
   */
  WorkspaceHandle prepareWorkspace(
      UUID workspaceId,
      UUID ownerId,
      String imageRef,
      long diskSizeBytes,
      long memoryMib,
      int vcpuCount,
      QuotaBytes quota);

  /**
   * Starts a workspace runtime.
   *
   * @param handle the storage handle
   * @param config the runtime configuration
   */
  void startRuntime(WorkspaceHandle handle, RuntimeRequest config);

  /**
   * Stops a workspace runtime.
   *
   * @param handle the storage handle
   * @param graceful true for guest shutdown, false for kill
   * @param timeoutSeconds the graceful timeout
   */
  void stopRuntime(WorkspaceHandle handle, boolean graceful, int timeoutSeconds);

  /**
   * Destroys a workspace shell.
   *
   * @param handle the storage handle
   * @param keepSnapshots whether snapshot subvolumes survive
   */
  void destroyWorkspace(WorkspaceHandle handle, boolean keepSnapshots);

  /**
   * Snapshots a workspace home.
   *
   * @param handle the storage handle
   * @param tag the snapshot tag
   * @param createdBy who triggered it
   * @return the snapshot handle
   */
  SnapshotHandle createSnapshot(WorkspaceHandle handle, SnapshotTag tag, String createdBy);

  /**
   * Restores a workspace home from a snapshot, keeping the current home as a snapshot.
   *
   * @param handle the storage handle
   * @param snapshot the snapshot handle
   */
  void restoreSnapshot(WorkspaceHandle handle, SnapshotHandle snapshot);

  /**
   * Deletes a snapshot subvolume.
   *
   * @param snapshot the snapshot handle
   */
  void deleteSnapshot(SnapshotHandle snapshot);

  /**
   * Lists a workspace's snapshots.
   *
   * @param handle the storage handle
   * @return the summaries
   */
  List<SnapshotSummary> listSnapshots(WorkspaceHandle handle);

  /**
   * Registers a port for gateway routing.
   *
   * @param handle the storage handle
   * @param port the bound port
   * @param protocol the transport protocol
   * @return the opaque vsock handle
   */
  String attachPort(WorkspaceHandle handle, int port, String protocol);

  /**
   * Unregisters a port.
   *
   * @param handle the storage handle
   * @param port the bound port
   */
  void detachPort(WorkspaceHandle handle, int port);
}
