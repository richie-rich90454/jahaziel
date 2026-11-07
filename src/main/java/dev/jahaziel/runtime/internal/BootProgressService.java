/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live boot phases per workspace. These phases describe workspace storage preparation and backend
 * lifecycle state only; terminal and task processes are started on demand. Entries are transient
 * and are cleared when the process restarts.
 */
public final class BootProgressService {
  /** Coarse boot phases visible to the workbench. */
  public enum BootPhase {
    PREPARING,
    STARTING,
    READY
  }

  /**
   * A phase report.
   *
   * @param correlationId the identifier shared by every phase of one start operation
   * @param phase the current sub-step
   * @param updatedAt when the workspace entered it
   */
  public record Progress(UUID correlationId, BootPhase phase, Instant updatedAt) {
    public Progress {
      Objects.requireNonNull(correlationId, "correlationId");
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(updatedAt, "updatedAt");
    }
  }

  private final ConcurrentHashMap<UUID, Progress> phases = new ConcurrentHashMap<>();

  /**
   * Records a phase for a workspace.
   *
   * @param id the workspace
   * @param phase the sub-step just entered
   */
  public void report(WorkspaceId id, BootPhase phase) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(phase, "phase");
    phases.compute(
        id.value(),
        (ignored, previous) -> {
          UUID correlationId =
              phase == BootPhase.PREPARING || previous == null
                  ? UUID.randomUUID()
                  : previous.correlationId();
          return new Progress(correlationId, phase, Instant.now());
        });
  }

  /**
   * Returns the latest report, if any.
   *
   * @param id the workspace
   * @return the progress, empty when nothing was reported (yet, or since a restart)
   */
  public Optional<Progress> snapshot(WorkspaceId id) {
    Objects.requireNonNull(id, "id");
    return Optional.ofNullable(phases.get(id.value()));
  }

  /**
   * Drops a workspace's report.
   *
   * @param id the workspace
   */
  public void clear(WorkspaceId id) {
    Objects.requireNonNull(id, "id");
    phases.remove(id.value());
  }

  /**
   * Returns every live report, for administration.
   *
   * @return an unmodifiable snapshot
   */
  public Map<UUID, Progress> snapshotAll() {
    return Map.copyOf(phases);
  }
}
