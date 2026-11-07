/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.security.api.SecretRedactor;
import dev.jahaziel.terminal.api.TerminalLimitException;
import dev.jahaziel.terminal.api.TerminalNotFoundException;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.api.TerminalService.StreamEvent;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
public final class TerminalServiceImpl implements TerminalService {
  private static final int MAX_SESSIONS = 128;
  private static final int MAX_SESSIONS_PER_WORKSPACE = 8;
  private static final int MAX_HISTORY_CHARS = 64 * 1024;
  private static final Duration IDLE_TTL = Duration.ofHours(12);

  private final WorkspaceDirectoryService directories;
  private final WorkspaceProcessRegistry processRegistry;
  private final WorkspaceService workspaces;
  private final boolean ptyEnabled;
  private final ConcurrentMap<TerminalSessionId, State> sessions = new ConcurrentHashMap<>();
  private final Set<WorkspaceId> stoppingWorkspaces = new HashSet<>();

  /** Registry-only constructor retained for isolated service tests. */
  public TerminalServiceImpl() {
    this(null, null, null, false);
  }

  /** Creates the production PTY service with process and workspace lifecycle checks. */
  public TerminalServiceImpl(
      WorkspaceDirectoryService directories,
      WorkspaceProcessRegistry processRegistry,
      WorkspaceService workspaces) {
    this(
        directories,
        Objects.requireNonNull(processRegistry, "processRegistry"),
        Objects.requireNonNull(workspaces, "workspaces"),
        true);
  }

  private TerminalServiceImpl(
      WorkspaceDirectoryService directories,
      WorkspaceProcessRegistry processRegistry,
      WorkspaceService workspaces,
      boolean ptyEnabled) {
    if (ptyEnabled) Objects.requireNonNull(directories, "directories");
    this.directories = directories;
    this.processRegistry = processRegistry;
    this.workspaces = workspaces;
    this.ptyEnabled = ptyEnabled;
  }

  @Override
  public synchronized TerminalSession open(
      WorkspaceId workspaceId, dev.jahaziel.identity.domain.UserId userId, int rows, int cols) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(userId, "userId");
    requireWorkspaceAvailable(workspaceId);
    reapIdle();
    if (sessions.size() >= MAX_SESSIONS) {
      throw new TerminalLimitException("terminal session limit reached");
    }
    if (sessions.values().stream()
            .filter(state -> state.session.workspaceId().equals(workspaceId))
            .count()
        >= MAX_SESSIONS_PER_WORKSPACE) {
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
                  workspaceId, WorkspaceProcessRegistry.Kind.TERMINAL, state.process);
        }
      } catch (RuntimeException failure) {
        closeProcess(state);
        throw failure;
      }
    }
    sessions.put(session.id(), state);
    if (state.process != null) {
      Thread.ofVirtual()
          .name("terminal-output-" + session.id().value())
          .start(() -> readOutput(state));
      Thread.ofVirtual()
          .name("terminal-exit-" + session.id().value())
          .start(() -> watchExit(state));
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
    return sessions.values().stream()
        .map(state -> state.session)
        .filter(session -> session.workspaceId().equals(workspaceId))
        .toList();
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
      if (!state.history.isEmpty()) {
        output.accept(new StreamEvent("output", state.history.toString(), null));
      }
      state.listeners.add(output);
      Integer exitCode = state.session.exitCode();
      if (exitCode != null) {
        output.accept(new StreamEvent("exit", null, exitCode));
      }
    }
    state.session.recordActivity();
    return () -> state.listeners.remove(output);
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
    sessions.values().stream()
        .filter(state -> state.session.workspaceId().equals(workspaceId))
        .map(state -> state.session.id())
        .toList()
        .forEach(this::closeIfPresent);
  }

  @Override
  public synchronized void allowByWorkspace(WorkspaceId workspaceId) {
    stoppingWorkspaces.remove(Objects.requireNonNull(workspaceId, "workspaceId"));
  }

  @Override
  public void closeAll() {
    List.copyOf(sessions.keySet()).forEach(this::closeIfPresent);
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
    var workspace = workspaces.get(workspaceId);
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
    if (Platforms.detect(null).os() != Platform.Os.LINUX) {
      throw new TerminalUnavailableException("interactive terminals require a Linux server");
    }
    Path home = directories.home(session.workspaceId());
    try {
      String shell = Files.isExecutable(Path.of("/bin/bash")) ? "/bin/bash" : "/bin/sh";
      Map<String, String> environment = terminalEnvironment(home);
      return new PtyProcessBuilder(new String[] {shell, "-i"})
          .setDirectory(home.toString())
          .setEnvironment(environment)
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

  private static void readOutput(State state) {
    try (var reader =
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
    sessions.forEach(
        (id, state) -> {
          if (state.session.lastActivityAt().isBefore(cutoff)) {
            idle.add(id);
          }
        });
    idle.forEach(this::closeIfPresent);
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
    List<ProcessHandle> descendants = process.descendants().toList();
    descendants.forEach(ProcessHandle::destroy);
    process.destroy();
    try {
      if (!process.waitFor(1, TimeUnit.SECONDS)) {
        descendants.forEach(ProcessHandle::destroyForcibly);
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
      listeners.forEach(
          listener -> {
            try {
              listener.accept(outputEvent);
            } catch (RuntimeException disconnected) {
              listeners.remove(listener);
            }
          });
    }

    private void emit(String output) {
      emit(new StreamEvent("output", output, null));
    }
  }
}
