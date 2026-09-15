/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.util.List;
import java.util.Objects;

/**
 * Lifecycle orchestration over a node backend. Runtime handles equal workspace ids by convention,
 * so a control-plane restart loses no addressing. Zero bandwidth means unset; the agent applies
 * node defaults.
 */
public final class RuntimeServiceImpl implements RuntimeService {
  private static final int DISK_IOPS_LIMIT = 0;
  private static final long UNSET_BANDWIDTH = 0;
  private static final int NETWORK_CONNECTIONS_LIMIT = 5000;
  private static final int PIDS_LIMIT = 4096;

  private final NodeBackend backend;
  private final WorkspaceService workspaces;
  private final ImageRef image;

  /**
   * Creates the service.
   *
   * @param backend the node backend
   * @param workspaces the workspace service
   * @param image the base image reference
   */
  public RuntimeServiceImpl(NodeBackend backend, WorkspaceService workspaces, ImageRef image) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.image = Objects.requireNonNull(image, "image");
  }

  @Override
  public Workspace start(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    backend.prepare(
        new PrepareRequest(
            id.value(),
            workspace.owner().value(),
            image,
            new QuotaBytes(workspace.quota().logicalBytes()),
            workspace.vm().config().vcpuCount(),
            workspace.vm().config().memoryMib()));
    workspace.prepare();
    workspaces.save(workspace);
    bootBackend(workspace);
    workspace.boot();
    workspaces.save(workspace);
    return workspace;
  }

  @Override
  public Workspace stop(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    // Nothing to stop when no runtime was ever started: a requested workspace is already parked,
    // and suspending then aborting it would be an illegal transition (and break delete).
    if (workspace.state() instanceof WorkspaceState.Requested
        || workspace.state() instanceof WorkspaceState.Suspended
        || workspace.state() instanceof WorkspaceState.Destroyed) {
      return workspace;
    }
    backend.stop(new RuntimeHandle(id.value()), true);
    try {
      workspace.suspend();
    } catch (IllegalStateException notRunning) {
      workspace.abortBoot();
    }
    workspaces.save(workspace);
    return workspace;
  }

  @Override
  public Workspace restart(WorkspaceId id) {
    Workspace stopped = stop(id);
    stopped.reboot();
    workspaces.save(stopped);
    return start(id);
  }

  @Override
  public Workspace resume(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    bootBackend(workspace);
    workspace.resume();
    workspaces.save(workspace);
    return workspace;
  }

  @Override
  public Workspace markReady(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    workspace.markReady();
    workspaces.save(workspace);
    return workspace;
  }

  @Override
  public Workspace markActive(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    workspace.start();
    workspaces.save(workspace);
    return workspace;
  }

  private void bootBackend(Workspace workspace) {
    backend.start(
        new RuntimeRequest(
            workspace.id().value(),
            workspace.vm().config().vcpuCount(),
            workspace.vm().config().memoryMib(),
            DISK_IOPS_LIMIT,
            UNSET_BANDWIDTH,
            UNSET_BANDWIDTH,
            NETWORK_CONNECTIONS_LIMIT,
            PIDS_LIMIT,
            List.of()));
  }
}
