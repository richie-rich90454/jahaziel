/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Notifies derived indexes of workspace file changes without depending on them. */
public final class WorkspaceFileEvents {
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<Listener>();

    private WorkspaceFileEvents() {}

    /** Receives a workspace file change for a given storage root. */
    public interface Listener {
        /** Called after a file was saved, renamed or deleted. */
        void changed(Path dataRoot, WorkspaceId workspaceId);
    }

    /** Registers a derived-index listener. */
    public static void register(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    /**
     * Notifies all registered listeners; a derived index failure never fails the file operation.
     */
    public static void changed(Path dataRoot, WorkspaceId workspaceId) {
        if (dataRoot == null || workspaceId == null) {
            return;
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.changed(dataRoot, workspaceId);
            } catch (RuntimeException ignored) {
                // A stale derived index is rebuilt by the next scheduled synchronization.
            }
        }
    }
}
