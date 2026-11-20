/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.api;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Terminal session registry. PTYs run in the trusted host account, rooted in the workspace home.
 */
public abstract class TerminalService {
    /**
     * Opens a session.
     *
     * @param workspaceId the workspace
     * @param userId the owning user
     * @param rows terminal rows
     * @param cols terminal columns
     * @return the session
     */
    public abstract TerminalSession open(
            WorkspaceId workspaceId, UserId userId, int rows, int cols);

    /**
     * Returns one session.
     *
     * @param id the session identifier
     * @return the session
     * @throws TerminalNotFoundException when the session does not exist
     */
    public abstract TerminalSession get(TerminalSessionId id);

    /**
     * Closes a session.
     *
     * @param id the session identifier
     * @throws TerminalNotFoundException when the session does not exist
     */
    public abstract void close(TerminalSessionId id);

    /**
     * Lists a workspace's sessions.
     *
     * @param workspaceId the workspace
     * @return the sessions
     */
    public abstract List<TerminalSession> listByWorkspace(WorkspaceId workspaceId);

    /**
     * Records activity on a session.
     *
     * @param id the session identifier
     * @throws TerminalNotFoundException when the session does not exist
     */
    public abstract void activity(TerminalSessionId id);

    /** Subscribes to process events and immediately replays buffered output and an exit status. */
    public abstract AutoCloseable attach(TerminalSessionId id, Consumer<StreamEvent> output);

    /** Writes terminal input to the process stdin. */
    public abstract void write(TerminalSessionId id, String input);

    /** Updates the PTY dimensions. */
    public abstract void resize(TerminalSessionId id, int rows, int cols);

    /** Closes every live PTY for a workspace. */
    public abstract void closeByWorkspace(WorkspaceId workspaceId);

    /** Allows new PTYs after the workspace has successfully started. */
    public void allowByWorkspace(WorkspaceId workspaceId) {}

    /** Closes every live PTY during server shutdown. */
    public abstract void closeAll();

    /** One stream event delivered to the browser. */
    public static final class StreamEvent {
        private final String type;
        private final String data;
        private final Integer exitCode;

        public StreamEvent(String type, String data, Integer exitCode) {
            this.type = type;
            this.data = data;
            this.exitCode = exitCode;
        }

        public String type() {
            return type;
        }

        public String data() {
            return data;
        }

        public Integer exitCode() {
            return exitCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StreamEvent)) return false;
            StreamEvent other = (StreamEvent) o;
            return Objects.equals(type, other.type)
                    && Objects.equals(data, other.data)
                    && Objects.equals(exitCode, other.exitCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, data, exitCode);
        }

        @Override
        public String toString() {
            return "StreamEvent[type=" + type + ", data=" + data + ", exitCode=" + exitCode + "]";
        }
    }
}
