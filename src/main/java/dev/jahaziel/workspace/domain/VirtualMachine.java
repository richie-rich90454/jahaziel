/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import java.time.Instant;
import java.util.Objects;

/** One virtual machine of a workspace. Tracked here, owned by the node agent. */
public final class VirtualMachine {
  private final VirtualMachineId id;
  private final WorkspaceId workspaceId;
  private final VirtualMachineConfiguration config;
  private VirtualMachineState state;
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
