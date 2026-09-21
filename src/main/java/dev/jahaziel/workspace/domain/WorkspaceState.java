/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import java.time.Instant;

/** Lifecycle state of a workspace. Impossible states are unrepresentable. */
public sealed interface WorkspaceState {
  /** Requested, waiting for admission. */
  record Requested(Instant at) implements WorkspaceState {}

  /** Storage and jail are being prepared. */
  record Preparing(Instant at) implements WorkspaceState {}

  /** The virtual machine is booting. */
  record Booting(Instant at) implements WorkspaceState {}

  /** Booted, waiting for the first user session. */
  record Ready(Instant at) implements WorkspaceState {}

  /** Serving a user session. */
  record Active(Instant at) implements WorkspaceState {}

  /** No input, socket, CPU or network activity for the idle window. */
  record Idle(Instant at, Instant since) implements WorkspaceState {}

  /** Stopped, home folder preserved. */
  record Suspended(Instant at) implements WorkspaceState {}

  /** Removed by request. */
  record Destroyed(Instant at) implements WorkspaceState {}

  /** Unrecoverable failure. */
  record Failed(Instant at, String reason) implements WorkspaceState {}
}
