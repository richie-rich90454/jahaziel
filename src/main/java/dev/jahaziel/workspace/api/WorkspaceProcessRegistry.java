/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Tracks workspace processes so interrupted server restarts can stop stale processes safely. */
public abstract class WorkspaceProcessRegistry {
    /** The server-managed process categories that may be restarted with the application. */
    public static final class Kind {
        public static final Kind TERMINAL = new Kind("TERMINAL", 0);
        public static final Kind TASK = new Kind("TASK", 1);
        public static final Kind SETUP = new Kind("SETUP", 2);
        public static final Kind LANGUAGE_SERVER = new Kind("LANGUAGE_SERVER", 3);
        public static final Kind DEBUG_ADAPTER = new Kind("DEBUG_ADAPTER", 4);
        private static final Kind[] VALUES = {
            TERMINAL, TASK, SETUP, LANGUAGE_SERVER, DEBUG_ADAPTER
        };

        private final String name;
        private final int ordinal;

        private Kind(String name, int ordinal) {
            this.name = name;
            this.ordinal = ordinal;
        }

        public String name() {
            return name;
        }

        public String getName() {
            return name;
        }

        public int ordinal() {
            return ordinal;
        }

        public int getOrdinal() {
            return ordinal;
        }

        public static Kind[] values() {
            return VALUES.clone();
        }

        public static Kind valueOf(String name) {
            for (Kind kind : VALUES) {
                if (kind.name.equals(name)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("No constant named " + name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Records a process and returns a handle that removes its marker when it exits. */
    public abstract Registration track(WorkspaceId workspaceId, Kind kind, Process process);

    /**
     * Wraps a server-owned command in its own Linux session so child processes can be recovered as
     * a group after an abrupt server restart.
     */
    public abstract List<String> dedicatedSessionCommand(List<String> command);

    /**
     * Lists live members of the server-managed process groups for one workspace. Only processes
     * whose root still matches its persisted identity are included.
     *
     * @param workspaceId the workspace whose processes may be inspected
     * @param kinds the managed process categories to include
     * @return process ids limited to the selected workspace and categories
     */
    public List<TrackedProcess> trackedProcessTree(WorkspaceId workspaceId, Set<Kind> kinds) {
        return List.of();
    }

    /** A process marker registration. Closing it is idempotent. */
    public abstract static class Registration implements AutoCloseable {
        public abstract UUID id();

        @Override
        public abstract void close();
    }

    /** A live process id and the kind of server-managed root that owns it. */
    public static final class TrackedProcess {
        private final Kind kind;
        private final long processId;

        public TrackedProcess(Kind kind, long processId) {
            if (kind == null) throw new NullPointerException("kind");
            if (processId < 1) throw new IllegalArgumentException("processId must be positive");

            this.kind = kind;
            this.processId = processId;
        }

        public Kind kind() {
            return kind;
        }

        public Kind getKind() {
            return kind;
        }

        public long processId() {
            return processId;
        }

        public long getProcessId() {
            return processId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrackedProcess other = (TrackedProcess) o;
            return Objects.equals(kind, other.kind) && processId == other.processId;
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(kind);
            result = 31 * result + (int) (processId ^ (processId >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "TrackedProcess{" + "kind=" + kind + ", processId=" + processId + '}';
        }
    }
}
