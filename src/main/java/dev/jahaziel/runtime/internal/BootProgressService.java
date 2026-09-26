/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import dev.jahaziel.ide.internal.IdeServerService.BootPhase;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live boot phases per workspace. The domain state machine only knows coarse states (preparing,
 * booting, ready), but a local start crosses real sub-steps — extracting the REH, spawning the IDE
 * server, waiting for its port, registering the gateway route — and the UI renders a determinate
 * progress bar from these instead of guessing. Entries are transient: a restart wipes them, and the
 * next start repopulates from the beginning.
 */
public final class BootProgressService {
  /**
   * A phase report.
   *
   * @param phase the current sub-step
   * @param updatedAt when the workspace entered it
   */
  public record Progress(BootPhase phase, Instant updatedAt) {
    public Progress {
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
    phases.put(id.value(), new Progress(phase, Instant.now()));
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
