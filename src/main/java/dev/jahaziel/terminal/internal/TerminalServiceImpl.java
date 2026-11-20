/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.platform.spi.ProcessEnvironment;
import dev.jahaziel.platform.spi.ProcessEnvironments;
import dev.jahaziel.security.api.SecretRedactor;
import dev.jahaziel.terminal.api.TerminalLimitException;
import dev.jahaziel.terminal.api.TerminalNotFoundException;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.api.TerminalUnavailableException;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Live terminal sessions backed by PTYs in the workspace home directory. */
public final class TerminalServiceImpl extends TerminalService {
    private static final int MAX_SESSIONS = 128;
    private static final int MAX_SESSIONS_PER_WORKSPACE = 8;
    private static final int MAX_HISTORY_CHARS = 64 * 1024;
    private static final Duration IDLE_TTL = Duration.ofHours(12);

    private final WorkspaceDirectoryService directories;
    private final WorkspaceProcessRegistry processRegistry;
    private final WorkspaceService workspaces;
    private final AgentToolService agentTools;
    private final boolean ptyEnabled;
    private final ConcurrentMap<TerminalSessionId, State> sessions = new ConcurrentHashMap<>();
    private final Set<WorkspaceId> stoppingWorkspaces = new HashSet<>();

    /** Registry-only constructor retained for isolated service tests. */
    public TerminalServiceImpl() {
        this(null, null, null, null, false);
    }

    /** Creates the production PTY service with process and workspace lifecycle checks. */
    public TerminalServiceImpl(
            WorkspaceDirectoryService directories,
            WorkspaceProcessRegistry processRegistry,
            WorkspaceService workspaces) {
        this(directories, processRegistry, workspaces, null);
    }

    /** Creates the production PTY service with per-user managed tool paths. */
    public TerminalServiceImpl(
            WorkspaceDirectoryService directories,
            WorkspaceProcessRegistry processRegistry,
            WorkspaceService workspaces,
            AgentToolService agentTools) {
        this(
                directories,
                Objects.requireNonNull(processRegistry, "processRegistry"),
                Objects.requireNonNull(workspaces, "workspaces"),
                agentTools,
                true);
    }

    private TerminalServiceImpl(
            WorkspaceDirectoryService directories,
            WorkspaceProcessRegistry processRegistry,
            WorkspaceService workspaces,
            AgentToolService agentTools,
            boolean ptyEnabled) {
        if (ptyEnabled) Objects.requireNonNull(directories, "directories");
        this.directories = directories;
        this.processRegistry = processRegistry;
        this.workspaces = workspaces;
        this.agentTools = agentTools;
        this.ptyEnabled = ptyEnabled;
    }

    @Override
    public synchronized TerminalSession open(
            WorkspaceId workspaceId,
            dev.jahaziel.identity.domain.UserId userId,
            int rows,
            int cols) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(userId, "userId");
        requireWorkspaceAvailable(workspaceId);
        reapIdle();
        if (sessions.size() >= MAX_SESSIONS) {
            throw new TerminalLimitException("terminal session limit reached");
        }
        int workspaceSessions = 0;
        for (State state : sessions.values()) {
            if (state.session.workspaceId().equals(workspaceId)) {
                workspaceSessions++;
            }
        }
        if (workspaceSessions >= MAX_SESSIONS_PER_WORKSPACE) {
            throw new TerminalLimitException("workspace terminal session limit reached");
        }
        TerminalSession session =
                new TerminalSession(
                        new TerminalSessionId(UUID.randomUUID()), workspaceId, userId, rows, cols);
        State state = new State(session);
        if (ptyEnabled) {
            try {
                state.process = startPty(session);
                if (processRegistry != null) {
                    state.registration =
                            processRegistry.track(
                                    workspaceId,
                                    WorkspaceProcessRegistry.Kind.TERMINAL,
                                    state.process);
                }
            } catch (RuntimeException failure) {
                closeProcess(state);
                throw failure;
            }
        }
        sessions.put(session.id(), state);
        if (state.process != null) {
            Thread.ofPlatform()
                    .daemon()
                    .name("terminal-output-" + session.id().value())
                    .start(
                            new Runnable() {
                                @Override
                                public void run() {
                                    readOutput(state);
                                }
                            });
            Thread.ofPlatform()
                    .daemon()
                    .name("terminal-exit-" + session.id().value())
                    .start(
                            new Runnable() {
                                @Override
                                public void run() {
                                    watchExit(state);
                                }
                            });
        }
        return session;
    }

    @Override
    public TerminalSession get(TerminalSessionId id) {
        return stateOf(id).session;
    }

    @Override
    public void close(TerminalSessionId id) {
        State state = sessions.remove(Objects.requireNonNull(id, "id"));
        if (state == null) {
            throw new TerminalNotFoundException(id);
        }
        closeProcess(state);
    }

    @Override
    public List<TerminalSession> listByWorkspace(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        List<TerminalSession> result = new ArrayList<TerminalSession>();
        for (State state : sessions.values()) {
            if (state.session.workspaceId().equals(workspaceId)) {
                result.add(state.session);
            }
        }
        return result;
    }

    @Override
    public void activity(TerminalSessionId id) {
        stateOf(id).session.recordActivity();
    }

    @Override
    public AutoCloseable attach(TerminalSessionId id, Consumer<StreamEvent> output) {
        Objects.requireNonNull(output, "output");
        State state = stateOf(id);
        if (state.process == null) {
            throw new IllegalStateException("interactive terminal processes are unavailable");
        }
        synchronized (state) {
            output.accept(new StreamEvent("history", state.history.toString(), null));
            state.listeners.add(output);
            Integer exitCode = state.session.exitCode();
            if (exitCode != null) {
                output.accept(new StreamEvent("exit", null, exitCode));
            }
        }
        state.session.recordActivity();
        return new AutoCloseable() {
            @Override
            public void close() {
                state.listeners.remove(output);
            }
        };
    }

    @Override
    public void write(TerminalSessionId id, String input) {
        Objects.requireNonNull(input, "input");
        if (input.length() > 16 * 1024) {
            throw new IllegalArgumentException("terminal input chunk is too large");
        }
        State state = stateOf(id);
        if (state.process == null || !state.process.isAlive()) {
            throw new IllegalStateException("terminal process has exited");
        }
        try {
            OutputStream stream = state.process.getOutputStream();
            stream.write(input.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            state.session.recordActivity();
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot write to terminal", failure);
        }
    }

    @Override
    public void resize(TerminalSessionId id, int rows, int cols) {
        State state = stateOf(id);
        state.session.resize(rows, cols);
        if (state.process != null && state.process.isAlive()) {
            state.process.setWinSize(new WinSize(cols, rows));
        }
    }

    @Override
    public synchronized void closeByWorkspace(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        stoppingWorkspaces.add(workspaceId);
        List<TerminalSessionId> matching = new ArrayList<TerminalSessionId>();
        for (State state : sessions.values()) {
            if (state.session.workspaceId().equals(workspaceId)) {
                matching.add(state.session.id());
            }
        }
        for (TerminalSessionId id : matching) {
            closeIfPresent(id);
        }
    }

    @Override
    public synchronized void allowByWorkspace(WorkspaceId workspaceId) {
        stoppingWorkspaces.remove(Objects.requireNonNull(workspaceId, "workspaceId"));
    }

    @Override
    public void closeAll() {
        for (TerminalSessionId id : new ArrayList<TerminalSessionId>(sessions.keySet())) {
            closeIfPresent(id);
        }
    }

    private State stateOf(TerminalSessionId id) {
        State state = sessions.get(Objects.requireNonNull(id, "id"));
        if (state == null) {
            throw new TerminalNotFoundException(id);
        }
        return state;
    }

    private void requireWorkspaceAvailable(WorkspaceId workspaceId) {
        if (workspaces == null) return;
        dev.jahaziel.workspace.domain.Workspace workspace = workspaces.get(workspaceId);
        WorkspaceState state = workspace.state();
        if (stoppingWorkspaces.contains(workspaceId)
                || !(state instanceof WorkspaceState.Ready
                        || state instanceof WorkspaceState.Active
                        || state instanceof WorkspaceState.Idle)) {
            throw new dev.jahaziel.workspace.domain.IllegalWorkspaceStateException(
                    workspaceId, state, "open terminal");
        }
    }

    private PtyProcess startPty(TerminalSession session) {
        ProcessEnvironment processEnvironment =
                ProcessEnvironments.forOs(Platforms.detect(null).os());
        if (!processEnvironment.isSupported()) {
            throw new TerminalUnavailableException(
                    "interactive terminals are not supported by "
                            + processEnvironment.descriptor().name());
        }
        Path home = directories.home(session.workspaceId());
        try {
            Map<String, String> extra = new LinkedHashMap<String, String>();
            if (agentTools != null) {
                agentTools.prepareProfile(session.userId());
                extra =
                        processEnvironment.toolEnvironment(
                                agentTools.executableDirectory(session.userId()),
                                agentTools.profileDirectory(session.userId()));
            }
            List<String> command = processEnvironment.shellCommand(new ArrayList<String>());
            return new PtyProcessBuilder(command.toArray(new String[0]))
                    .setDirectory(home.toString())
                    .setEnvironment(processEnvironment.environment(home, extra))
                    .setInitialRows(session.rowCount())
                    .setInitialColumns(session.colCount())
                    .setRedirectErrorStream(true)
                    .start();
        } catch (IOException failure) {
            throw new TerminalUnavailableException("cannot start workspace terminal", failure);
        }
    }

    /** Returns only the safe, server-owned values available to workspace shell processes. */
    static Map<String, String> terminalEnvironment(Path home) {
        Objects.requireNonNull(home, "home");
        return Map.of(
                "HOME", home.toString(),
                "PATH", "/usr/local/bin:/usr/bin:/bin",
                "TERM", "xterm-256color",
                "LANG", "C.UTF-8");
    }

    /** Returns the workspace environment with the user's private tool and CLI profile paths. */
    static Map<String, String> terminalEnvironment(Path home, Path toolBin, Path profile) {
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(toolBin, "toolBin");
        Objects.requireNonNull(profile, "profile");
        return Map.of(
                "HOME",
                home.toString(),
                "PATH",
                toolBin + java.io.File.pathSeparator + "/usr/local/bin:/usr/bin:/bin",
                "XDG_CONFIG_HOME",
                profile.resolve("config").toString(),
                "XDG_DATA_HOME",
                profile.resolve("data").toString(),
                "XDG_CACHE_HOME",
                profile.resolve("cache").toString(),
                "TERM",
                "xterm-256color",
                "LANG",
                "C.UTF-8");
    }

    private static void readOutput(State state) {
        try (InputStreamReader reader =
                new InputStreamReader(state.process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (count > 0) {
                    state.emit(new String(buffer, 0, count));
                }
            }
        } catch (IOException failure) {
            if (state.process.isAlive()) {
                state.emit("\r\n[jahaziel] terminal output stream closed\r\n");
            }
        } finally {
            state.flushOutput();
        }
    }

    private void watchExit(State state) {
        try {
            int exit = state.process.waitFor();
            state.session.markExited(exit);
            state.emit(new StreamEvent("exit", null, exit));
            state.process.destroy();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } finally {
            closeRegistration(state);
        }
    }

    private synchronized void reapIdle() {
        Instant cutoff = Instant.now().minus(IDLE_TTL);
        List<TerminalSessionId> idle = new ArrayList<>();
        for (Map.Entry<TerminalSessionId, State> entry : sessions.entrySet()) {
            if (entry.getValue().session.lastActivityAt().isBefore(cutoff)) {
                idle.add(entry.getKey());
            }
        }
        for (TerminalSessionId id : idle) {
            closeIfPresent(id);
        }
    }

    private void closeIfPresent(TerminalSessionId id) {
        State state = sessions.remove(id);
        if (state != null) {
            closeProcess(state);
        }
    }

    private static void closeProcess(State state) {
        PtyProcess process = state.process;
        if (process == null) {
            closeRegistration(state);
            return;
        }
        ProcessHandle handle = ProcessHandle.of(process.pid()).orElse(null);
        List<ProcessHandle> descendants =
                handle == null ? List.of() : handle.descendants().toList();
        for (ProcessHandle descendant : descendants) {
            descendant.destroy();
        }
        process.destroy();
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                for (ProcessHandle descendant : descendants) {
                    descendant.destroyForcibly();
                }
                process.destroyForcibly();
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        try {
            process.getInputStream().close();
            process.getOutputStream().close();
            process.getErrorStream().close();
        } catch (IOException ignored) {
            // Stream closure is best-effort after the process tree has been stopped.
        }
        closeRegistration(state);
    }

    private static void closeRegistration(State state) {
        WorkspaceProcessRegistry.Registration registration = state.registration;
        state.registration = null;
        if (registration != null) registration.close();
    }

    private static final class State {
        private final TerminalSession session;
        private final StringBuilder history = new StringBuilder();
        private final SecretRedactor.Stream redactor = new SecretRedactor.Stream();
        private final CopyOnWriteArraySet<Consumer<StreamEvent>> listeners =
                new CopyOnWriteArraySet<>();
        private volatile PtyProcess process;
        private volatile WorkspaceProcessRegistry.Registration registration;

        private State(TerminalSession session) {
            this.session = session;
        }

        private void emit(StreamEvent event) {
            if ("output".equals(event.type()) && event.data() != null) {
                String safe = redactor.accept(event.data());
                if (!safe.isEmpty()) publish(new StreamEvent(event.type(), safe, event.exitCode()));
                return;
            }
            publish(event);
        }

        private void flushOutput() {
            String safe = redactor.finish();
            if (!safe.isEmpty()) publish(new StreamEvent("output", safe, null));
        }

        private void publish(StreamEvent event) {
            if ("output".equals(event.type()) && event.data() != null) {
                synchronized (this) {
                    history.append(event.data());
                    if (history.length() > MAX_HISTORY_CHARS) {
                        history.delete(0, history.length() - MAX_HISTORY_CHARS);
                    }
                }
            }
            StreamEvent outputEvent = event;
            for (Consumer<StreamEvent> listener : listeners) {
                try {
                    listener.accept(outputEvent);
                } catch (RuntimeException disconnected) {
                    listeners.remove(listener);
                }
            }
        }

        private void emit(String output) {
            emit(new StreamEvent("output", output, null));
        }
    }
}
