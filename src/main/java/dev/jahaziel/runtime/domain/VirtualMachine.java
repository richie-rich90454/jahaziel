/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.domain;

import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One virtual machine of a workspace. Tracked here, owned by the node agent. */
public final class VirtualMachine {
  private final VirtualMachineId id;
  private final WorkspaceId workspaceId;
  private final VirtualMachineConfiguration config;
  private VirtualMachineState state;
  private final List<TerminalSession> terminals = new ArrayList<>();
  private RuntimeMetrics metrics = RuntimeMetrics.zero();

  /**
   * Creates a stopped virtual machine.
   *
   * @param id the machine identifier
   * @param workspaceId the workspace
   * @param config the sizing
   */
  public VirtualMachine(
      VirtualMachineId id, WorkspaceId workspaceId, VirtualMachineConfiguration config) {
    this.id = Objects.requireNonNull(id, "id");
    this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
    this.config = Objects.requireNonNull(config, "config");
    this.state = new VirtualMachineState.Stopped(Instant.now());
  }

  /** Returns the machine identifier. */
  public VirtualMachineId id() {
    return id;
  }

  /** Returns the workspace. */
  public WorkspaceId workspaceId() {
    return workspaceId;
  }

  /** Returns the sizing. */
  public VirtualMachineConfiguration config() {
    return config;
  }

  /** Returns the current state. */
  public VirtualMachineState state() {
    return state;
  }

  /**
   * Boots the machine.
   *
   * @param hostname the guest hostname
   */
  public void boot(String hostname) {
    if (!(state instanceof VirtualMachineState.Stopped)) {
      throw new IllegalStateException("Machine " + id + " cannot boot while " + state);
    }
    state = new VirtualMachineState.Running(Instant.now(), hostname);
  }

  /**
   * Marks the machine crashed.
   *
   * @param reason the crash reason
   */
  public void crash(String reason) {
    if (!(state instanceof VirtualMachineState.Running)) {
      throw new IllegalStateException("Machine " + id + " cannot crash while " + state);
    }
    state = new VirtualMachineState.Crashed(Instant.now(), reason);
  }

  /** Stops the machine. */
  public void stop() {
    if (!(state instanceof VirtualMachineState.Running)) {
      throw new IllegalStateException("Machine " + id + " cannot stop while " + state);
    }
    state = new VirtualMachineState.Stopped(Instant.now());
  }

  /**
   * Attaches a terminal session.
   *
   * @param session the session to attach
   */
  public void attachTerminal(TerminalSession session) {
    Objects.requireNonNull(session, "session");
    if (!(state instanceof VirtualMachineState.Running)) {
      throw new IllegalStateException("Machine " + id + " cannot attach terminals while " + state);
    }
    terminals.add(session);
  }

  /**
   * Detaches a terminal session.
   *
   * @param sessionId the session identifier
   */
  public void detachTerminal(TerminalSessionId sessionId) {
    boolean removed = terminals.removeIf(session -> session.id().equals(sessionId));
    if (!removed) {
      throw new IllegalArgumentException("Unknown terminal session " + sessionId);
    }
  }

  /**
   * Returns the attached terminal sessions.
   *
   * @return a defensive copy
   */
  public List<TerminalSession> terminals() {
    return List.copyOf(terminals);
  }

  /** Returns the latest resource sample. */
  public RuntimeMetrics metrics() {
    return metrics;
  }

  /**
   * Records a resource sample.
   *
   * @param sample the latest sample
   */
  public void updateMetrics(RuntimeMetrics sample) {
    metrics = Objects.requireNonNull(sample, "sample");
  }
}
