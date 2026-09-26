/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import dev.jahaziel.ide.internal.IdeServerService;
import dev.jahaziel.ide.internal.IdeServerService.BootPhase;
import dev.jahaziel.ide.internal.RehNotInstalledException;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.IllegalWorkspaceStateException;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle orchestration over a node backend. Runtime handles equal workspace ids by convention,
 * so a control-plane restart loses no addressing. Zero bandwidth means unset; the agent applies
 * node defaults.
 */
public final class RuntimeServiceImpl implements RuntimeService {
  private static final Logger log = LoggerFactory.getLogger(RuntimeServiceImpl.class);
  private static final int DISK_IOPS_LIMIT = 0;
  private static final long UNSET_BANDWIDTH = 0;
  private static final int NETWORK_CONNECTIONS_LIMIT = 5000;
  private static final int PIDS_LIMIT = 4096;

  private final NodeBackend backend;
  private final WorkspaceService workspaces;
  private final ImageRef image;
  private final IdeServerService ideServers;
  private final PortService ports;
  private final BootProgressService progress;

  /**
   * Creates the service.
   *
   * @param backend the node backend
   * @param workspaces the workspace service
   * @param image the base image reference
   * @param ideServers the per-workspace IDE servers
   * @param ports the gateway port bindings
   * @param progress the live boot-phase reports
   */
  public RuntimeServiceImpl(
      NodeBackend backend,
      WorkspaceService workspaces,
      ImageRef image,
      IdeServerService ideServers,
      PortService ports,
      BootProgressService progress) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.image = Objects.requireNonNull(image, "image");
    this.ideServers = Objects.requireNonNull(ideServers, "ideServers");
    this.ports = Objects.requireNonNull(ports, "ports");
    this.progress = Objects.requireNonNull(progress, "progress");
  }

  @Override
  public Workspace start(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    if (workspace.state() instanceof WorkspaceState.Requested) {
      progress.report(id, BootPhase.PREPARING);
      backend.prepare(prepareRequest(workspace));
      workspace.prepare();
      workspaces.save(workspace);
      bootBackend(workspace);
      workspace.boot();
      workspaces.save(workspace);
    } else if (backend instanceof LocalProcessBackend
        && workspace.state() instanceof WorkspaceState.Preparing) {
      progress.report(id, BootPhase.PREPARING);
      backend.prepare(prepareRequest(workspace));
      bootBackend(workspace);
      workspace.boot();
      workspaces.save(workspace);
    } else if (!(backend instanceof LocalProcessBackend
        && (workspace.state() instanceof WorkspaceState.Booting
            || workspace.state() instanceof WorkspaceState.Ready
            || workspace.state() instanceof WorkspaceState.Active))) {
      throw new IllegalWorkspaceStateException(id, workspace.state(), "start");
    }
    if (backend instanceof LocalProcessBackend) {
      WorkspaceHandle handle = backend.prepare(prepareRequest(workspace));
      LocalProcessBackend deployment = (LocalProcessBackend) backend;
      if (!deployment.isRunning(id.value())) {
        backend.start(runtimeRequest(workspace));
      }
      return finishBoot(workspace, handle);
    }
    return workspace;
  }

  @Override
  public Workspace stop(WorkspaceId id) {
    ideServers.stop(id);
    progress.clear(id);
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
    WorkspaceHandle handle = backend.prepare(prepareRequest(workspace));
    backend.start(runtimeRequest(workspace));
    workspace.resume();
    workspaces.save(workspace);
    if (backend instanceof LocalProcessBackend) {
      return finishBoot(workspace, handle);
    }
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
    backend.start(runtimeRequest(workspace));
  }

  private PrepareRequest prepareRequest(Workspace workspace) {
    return new PrepareRequest(
        workspace.id().value(),
        workspace.owner().value(),
        image,
        new QuotaBytes(workspace.quota().logicalBytes()),
        workspace.vm().config().vcpuCount(),
        workspace.vm().config().memoryMib());
  }

  private RuntimeRequest runtimeRequest(Workspace workspace) {
    return new RuntimeRequest(
        workspace.id().value(),
        workspace.vm().config().vcpuCount(),
        workspace.vm().config().memoryMib(),
        DISK_IOPS_LIMIT,
        UNSET_BANDWIDTH,
        UNSET_BANDWIDTH,
        NETWORK_CONNECTIONS_LIMIT,
        PIDS_LIMIT,
        List.of());
  }

  /**
   * Brings an already-booted workspace online: ensures its IDE server, drops bindings whose ports
   * no longer listen, registers the IDE port, and marks the workspace ready when it is still
   * booting. Without an installed REH artifact there is nothing to launch, so the workspace stays
   * booting; {@code doctor} fails closed on the missing artifact instead.
   */
  private Workspace finishBoot(Workspace workspace, WorkspaceHandle handle) {
    int port;
    try {
      port =
          ideServers.ensureRunning(
              workspace.id(), handle.homePath(), phase -> progress.report(workspace.id(), phase));
    } catch (RehNotInstalledException missing) {
      log.warn("workspace {} booted without IDE: {}", workspace.id(), missing.getMessage());
      return workspace;
    }
    progress.report(workspace.id(), BootPhase.REGISTERING);
    removeDeadBindings(workspace);
    boolean bound =
        ports.bindingsByWorkspace(workspace.id()).stream()
            .anyMatch(binding -> binding.port() == port);
    if (!bound) {
      ports.registerPort(workspace.owner(), port, workspace.id(), "http");
    }
    if (workspace.state() instanceof WorkspaceState.Booting) {
      workspace.markReady();
      workspaces.save(workspace);
    }
    progress.report(workspace.id(), BootPhase.READY);
    return workspace;
  }

  private void removeDeadBindings(Workspace workspace) {
    for (PortBinding binding : ports.bindingsByWorkspace(workspace.id())) {
      if (!listening(binding.port())) {
        ports.unregisterPort(workspace.owner(), binding.port());
      }
    }
  }

  private static boolean listening(int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
      return true;
    } catch (IOException notListening) {
      return false;
    }
  }
}
