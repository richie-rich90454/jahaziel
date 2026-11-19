/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.session;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages named PTY sessions for one user and workspace. The only way to reach the agent binary is
 * through {@link AgentToolService}; callers never supply an executable path or command.
 */
public final class AgentSessionService {
    private static final String TOOL_ID = "opencode";
    private static final String CREATED = "CREATED";
    private static final String RUNNING = "RUNNING";
    private static final String KILLED = "KILLED";

    private final AgentStore store;
    private final AgentToolService tools;

    public AgentSessionService(AgentStore store, AgentToolService tools) {
        this.store = Objects.requireNonNull(store, "store");
        this.tools = Objects.requireNonNull(tools, "tools");
    }

    /**
     * Creates a named session. The name comes from the browser; the executable never does.
     *
     * @param user the owning user
     * @param workspace the owning workspace
     * @param name a short session name
     * @return the new session
     */
    public AgentSession create(UserId user, WorkspaceId workspace, String name) {
        requireOwner(user, workspace);
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new AgentException("session name must be 1 to 64 safe characters");
        }
        requireInstalledTool(user);
        tools.prepareProfile(user);
        Path executable = tools.executableDirectory(user);
        if (executable == null) {
            throw new AgentException("the managed agent executable is unavailable");
        }

        SessionIndex index = load(user, workspace);
        for (AgentSession existing : index.sessions) {
            if (name.equals(existing.getName()) && !KILLED.equals(existing.getStatus())) {
                throw new AgentException("a live session with that name already exists");
            }
        }
        String now = Instant.now().toString();
        AgentSession session = new AgentSession();
        session.setId(UUID.randomUUID().toString());
        session.setName(name);
        session.setUserId(user.value().toString());
        session.setWorkspaceId(workspace.value().toString());
        session.setStatus(CREATED);
        session.setToolId(TOOL_ID);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        index.sessions.add(session);
        save(user, workspace, index);
        appendLine(
                user, workspace, session.getId(), "session created with managed tool " + TOOL_ID);
        return session;
    }

    /** Lists all sessions for one user and workspace. */
    public List<AgentSession> list(UserId user, WorkspaceId workspace) {
        requireOwner(user, workspace);
        SessionIndex index = load(user, workspace);
        return new ArrayList<AgentSession>(index.sessions);
    }

    /** Moves a session to RUNNING after a resume. */
    public AgentSession resume(UserId user, WorkspaceId workspace, String sessionId) {
        return transition(user, workspace, sessionId, RUNNING, "resumed");
    }

    /** Restarts a session while keeping its transcript. */
    public AgentSession restart(UserId user, WorkspaceId workspace, String sessionId) {
        return transition(user, workspace, sessionId, RUNNING, "restarted");
    }

    /** Stops a session. The agent process is never referenced by a browser-supplied path. */
    public AgentSession kill(UserId user, WorkspaceId workspace, String sessionId) {
        return transition(user, workspace, sessionId, KILLED, "killed");
    }

    /** Returns the current status of one session. */
    public AgentSession status(UserId user, WorkspaceId workspace, String sessionId) {
        requireOwner(user, workspace);
        return find(user, workspace, sessionId);
    }

    /** Appends one transcript line to a session. */
    public void appendLine(UserId user, WorkspaceId workspace, String sessionId, String line) {
        requireOwner(user, workspace);
        find(user, workspace, sessionId);
        if (line == null || line.isEmpty() || line.length() > 8192) {
            throw new AgentException("transcript line must be 1 to 8192 characters");
        }
        store.appendLine(transcriptPath(user, workspace, sessionId), line.replace('\n', ' '));
    }

    /** Returns the persisted transcript for one session. */
    public List<String> transcript(UserId user, WorkspaceId workspace, String sessionId) {
        requireOwner(user, workspace);
        find(user, workspace, sessionId);
        return store.readLines(transcriptPath(user, workspace, sessionId));
    }

    private AgentSession transition(
            UserId user, WorkspaceId workspace, String sessionId, String status, String note) {
        requireOwner(user, workspace);
        SessionIndex index = load(user, workspace);
        AgentSession target = findIn(index, sessionId);
        target.setStatus(status);
        target.setUpdatedAt(Instant.now().toString());
        save(user, workspace, index);
        appendLine(user, workspace, sessionId, "session " + note);
        return target;
    }

    private void requireInstalledTool(UserId user) {
        for (AgentToolService.ToolStatus status : tools.catalog(user)) {
            if (TOOL_ID.equals(status.id()) && "INSTALLED".equals(status.status())) {
                return;
            }
        }
        throw new AgentException("the managed agent tool is not installed for this user");
    }

    private AgentSession find(UserId user, WorkspaceId workspace, String sessionId) {
        return findIn(load(user, workspace), sessionId);
    }

    private static AgentSession findIn(SessionIndex index, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new AgentException("session id is required");
        }
        for (AgentSession session : index.sessions) {
            if (sessionId.equals(session.getId())) {
                return session;
            }
        }
        throw new AgentException("unknown agent session");
    }

    private SessionIndex load(UserId user, WorkspaceId workspace) {
        SessionIndex index = store.read(indexPath(user, workspace), SessionIndex.class);
        if (index == null) {
            index = new SessionIndex();
        }
        if (index.sessions == null) {
            index.sessions = new ArrayList<AgentSession>();
        }
        return index;
    }

    private void save(UserId user, WorkspaceId workspace, SessionIndex index) {
        store.write(indexPath(user, workspace), index);
    }

    private Path indexPath(UserId user, WorkspaceId workspace) {
        return store.containedChild(
                store.sessionRoot(),
                user.value().toString(),
                workspace.value().toString(),
                "index.json");
    }

    private Path transcriptPath(UserId user, WorkspaceId workspace, String sessionId) {
        return store.containedChild(
                store.sessionRoot(),
                user.value().toString(),
                workspace.value().toString(),
                sessionId,
                "transcript.log");
    }

    private static void requireOwner(UserId user, WorkspaceId workspace) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(workspace, "workspace");
    }

    /** Persisted index of sessions for one user and workspace. */
    public static final class SessionIndex {
        private List<AgentSession> sessions = new ArrayList<AgentSession>();

        public List<AgentSession> getSessions() {
            return sessions;
        }

        public void setSessions(List<AgentSession> sessions) {
            this.sessions = sessions;
        }
    }
}
