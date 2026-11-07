/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.runtime.internal.BootProgressService.BootPhase;
import dev.jahaziel.security.api.SecretRedactor;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.api.WorkspaceProcessLifecycle;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.IllegalWorkspaceStateException;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
  private final PortService ports;
  private final BootProgressService progress;
  private final TerminalService terminals;
  private final WorkspaceTaskService tasks;
  private final WorkspaceProcessLifecycle languageProcesses;

  /**
   * Creates the service.
   *
   * @param backend the node backend
   * @param workspaces the workspace service
   * @param image the base image reference
   * @param ports the gateway port bindings
   * @param progress the live boot-phase reports
   */
  public RuntimeServiceImpl(
      NodeBackend backend,
      WorkspaceService workspaces,
      ImageRef image,
      PortService ports,
      BootProgressService progress) {
    this(backend, workspaces, image, ports, progress, null);
  }

  /** Creates the service with terminal lifecycle coordination. */
  public RuntimeServiceImpl(
      NodeBackend backend,
      WorkspaceService workspaces,
      ImageRef image,
      PortService ports,
      BootProgressService progress,
      TerminalService terminals) {
    this(backend, workspaces, image, ports, progress, terminals, null);
  }

  /** Creates the service with terminal and task lifecycle coordination. */
  public RuntimeServiceImpl(
      NodeBackend backend,
      WorkspaceService workspaces,
      ImageRef image,
      PortService ports,
      BootProgressService progress,
      TerminalService terminals,
      WorkspaceTaskService tasks) {
    this(backend, workspaces, image, ports, progress, terminals, tasks, null);
  }

  /** Creates the service with lifecycle coordination for every workspace process type. */
  public RuntimeServiceImpl(
      NodeBackend backend,
      WorkspaceService workspaces,
      ImageRef image,
      PortService ports,
      BootProgressService progress,
      TerminalService terminals,
      WorkspaceTaskService tasks,
      WorkspaceProcessLifecycle languageProcesses) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.image = Objects.requireNonNull(image, "image");
    this.ports = Objects.requireNonNull(ports, "ports");
    this.progress = Objects.requireNonNull(progress, "progress");
    this.terminals = terminals;
    this.tasks = tasks;
    this.languageProcesses = languageProcesses;
  }

  @Override
  public Workspace start(WorkspaceId id) {
    try {
      Workspace started = doStart(id);
      if (languageProcesses != null) {
        languageProcesses.allowByWorkspace(id);
      }
      if (tasks != null) {
        tasks.allowByWorkspace(id);
      }
      if (terminals != null) {
        terminals.allowByWorkspace(id);
      }
      startPendingSetup(id);
      return started;
    } catch (RuntimeException failure) {
      abortToFailed(id, failure);
      throw failure;
    }
  }

  /**
   * Parks a workspace whose boot threw, so the failure is visible with its reason instead of
   * leaving the workspace in a transitional state no retry can explain. The original exception
   * still propagates to the caller.
   */
  private void abortToFailed(WorkspaceId id, RuntimeException failure) {
    UUID correlationId =
        progress
            .snapshot(id)
            .map(BootProgressService.Progress::correlationId)
            .orElseGet(UUID::randomUUID);
    progress.clear(id);
    Workspace current;
    try {
      current = workspaces.get(id);
    } catch (WorkspaceNotFoundException missing) {
      log.warn(
          "workspace start failed correlationId={} workspaceId={} reason=workspace-not-found",
          correlationId,
          id);
      return;
    }
    if (current.state() instanceof WorkspaceState.Destroyed) {
      return;
    }
    String reason = failure.getMessage();
    String safeReason =
        SecretRedactor.redact(
            reason == null || reason.isBlank() ? failure.getClass().getSimpleName() : reason);
    current.fail(safeReason + " (operation " + correlationId + ")");
    workspaces.save(current);
    log.warn(
        "workspace start failed correlationId={} workspaceId={} reason={}",
        correlationId,
        id,
        safeReason);
  }

  private Workspace doStart(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    if (workspace.state() instanceof WorkspaceState.Suspended
        || workspace.state() instanceof WorkspaceState.Failed) {
      workspace.reboot();
      workspaces.save(workspace);
    }
    if (workspace.state() instanceof WorkspaceState.Requested) {
      progress.report(id, BootPhase.PREPARING);
      backend.prepare(prepareRequest(workspace));
      workspace.prepare();
      workspaces.save(workspace);
      progress.report(id, BootPhase.STARTING);
      backend.start(runtimeRequest(workspace));
      workspace.boot();
      workspaces.save(workspace);
      workspace.markReady();
      workspaces.save(workspace);
      progress.report(id, BootPhase.READY);
    } else if (workspace.state() instanceof WorkspaceState.Preparing) {
      progress.report(id, BootPhase.PREPARING);
      backend.prepare(prepareRequest(workspace));
      progress.report(id, BootPhase.STARTING);
      backend.start(runtimeRequest(workspace));
      workspace.boot();
      workspace.markReady();
      workspaces.save(workspace);
      progress.report(id, BootPhase.READY);
    } else if (workspace.state() instanceof WorkspaceState.Booting) {
      backend.prepare(prepareRequest(workspace));
      backend.start(runtimeRequest(workspace));
      workspace.markReady();
      workspaces.save(workspace);
      progress.report(id, BootPhase.READY);
    } else if (!(workspace.state() instanceof WorkspaceState.Ready
        || workspace.state() instanceof WorkspaceState.Active
        || workspace.state() instanceof WorkspaceState.Idle)) {
      throw new IllegalWorkspaceStateException(id, workspace.state(), "start");
    }
    return workspace;
  }

  @Override
  public Workspace stop(WorkspaceId id) {
    progress.clear(id);
    if (languageProcesses != null) {
      languageProcesses.closeByWorkspace(id);
    }
    if (terminals != null) {
      terminals.closeByWorkspace(id);
    }
    if (tasks != null) {
      tasks.closeByWorkspace(id);
    }
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
  public void destroy(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    stop(id);
    ports.unregisterWorkspacePorts(id);
    backend.destroy(backend.prepare(prepareRequest(workspace)));
  }

  @Override
  public Workspace resume(WorkspaceId id) {
    Workspace workspace = workspaces.get(id);
    backend.prepare(prepareRequest(workspace));
    backend.start(runtimeRequest(workspace));
    workspace.resume();
    workspaces.save(workspace);
    if (languageProcesses != null) {
      languageProcesses.allowByWorkspace(id);
    }
    if (tasks != null) {
      tasks.allowByWorkspace(id);
    }
    if (terminals != null) {
      terminals.allowByWorkspace(id);
    }
    progress.report(id, BootPhase.READY);
    startPendingSetup(id);
    return workspace;
  }

  private void startPendingSetup(WorkspaceId id) {
    if (tasks == null) return;
    try {
      WorkspaceTaskService.SetupStatus setup = tasks.setupStatus(id);
      if (setup != null && "PENDING".equals(setup.status())) {
        tasks.retrySetup(id);
      }
    } catch (RuntimeException failure) {
      log.warn(
          "workspace started but pending template setup could not be scheduled workspaceId={} reason={}",
          id,
          SecretRedactor.redact(
              failure.getMessage() == null
                  ? failure.getClass().getSimpleName()
                  : failure.getMessage()));
    }
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
}
