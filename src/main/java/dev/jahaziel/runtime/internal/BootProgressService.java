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
import java.util.function.BiFunction;

/**
 * Live boot phases per workspace. These phases describe workspace storage preparation and backend
 * lifecycle state only; terminal and task processes are started on demand. Entries are transient
 * and are cleared when the process restarts.
 */
public final class BootProgressService {
    /** Coarse boot phases visible to the workbench. */
    public static final class BootPhase {
        /** Storage preparation. */
        public static final BootPhase PREPARING = new BootPhase("PREPARING");

        /** Backend start. */
        public static final BootPhase STARTING = new BootPhase("STARTING");

        /** Boot complete. */
        public static final BootPhase READY = new BootPhase("READY");

        private final String name;

        private BootPhase(String name) {
            this.name = name;
        }

        /** Returns the phase name. */
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** A phase report. */
    public static final class Progress {
        private final UUID correlationId;
        private final BootPhase phase;
        private final Instant updatedAt;

        /** Creates the report, validating every component. */
        public Progress(UUID correlationId, BootPhase phase, Instant updatedAt) {
            this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
            this.phase = Objects.requireNonNull(phase, "phase");
            this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        }

        /** Returns the identifier shared by every phase of one start operation. */
        public UUID correlationId() {
            return correlationId;
        }

        /** Returns the current sub-step. */
        public BootPhase phase() {
            return phase;
        }

        /** Returns when the workspace entered it. */
        public Instant updatedAt() {
            return updatedAt;
        }

        /** Compares by class and every component. */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Progress other = (Progress) o;
            return Objects.equals(correlationId, other.correlationId)
                    && Objects.equals(phase, other.phase)
                    && Objects.equals(updatedAt, other.updatedAt);
        }

        /** Returns a hash derived from every component. */
        @Override
        public int hashCode() {
            return Objects.hash(correlationId, phase, updatedAt);
        }

        /** Returns a readable representation. */
        @Override
        public String toString() {
            return "Progress[correlationId="
                    + correlationId
                    + ", phase="
                    + phase
                    + ", updatedAt="
                    + updatedAt
                    + "]";
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
                new BiFunction<UUID, Progress, Progress>() {
                    @Override
                    public Progress apply(UUID ignored, Progress previous) {
                        UUID correlationId =
                                phase == BootPhase.PREPARING || previous == null
                                        ? UUID.randomUUID()
                                        : previous.correlationId();
                        return new Progress(correlationId, phase, Instant.now());
                    }
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
