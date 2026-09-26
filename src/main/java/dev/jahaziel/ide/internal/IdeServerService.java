/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ide.internal;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One code-server per workspace on loopback. Tracks the child process per workspace so repeated
 * starts reuse it and stops reap it; a restart that lost the handle spawns fresh on a new port, and
 * the caller rebinds the gateway route.
 */
public final class IdeServerService {
  /** Ordered boot sub-steps. Declaration order is the progress order. */
  public enum BootPhase {
    PREPARING,
    EXTRACTING,
    SPAWNING,
    WAITING,
    REGISTERING,
    READY
  }

  /** Starts an OS process; the production starter shells out, tests substitute a fake. */
  @FunctionalInterface
  public interface ProcessStarter {
    /**
     * Starts a process.
     *
     * @param command the argv, executable first
     * @param workDir the child working directory
     * @return the running process
     * @throws IOException when the OS refuses to start it
     */
    Process start(List<String> command, Path workDir) throws IOException;
  }

  /** Allocates a loopback port; may throw when the OS has none to give. */
  @FunctionalInterface
  public interface PortAllocator {
    /**
     * Returns a currently-free loopback port.
     *
     * @return the port
     * @throws IOException when no port can be allocated
     */
    int allocate() throws IOException;
  }

  private record RunningServer(int port, Process process) {}

  private final IdeService ide;
  private final ProcessStarter starter;
  private final PortAllocator ports;
  private final Duration startTimeout;
  private final Duration pollInterval;
  private final ConcurrentHashMap<UUID, RunningServer> servers = new ConcurrentHashMap<>();

  /**
   * Creates the service with production defaults.
   *
   * @param ide the extracted REH builds
   */
  public IdeServerService(IdeService ide) {
    this(
        ide,
        IdeServerService::launch,
        IdeServerService::freePort,
        Duration.ofSeconds(30),
        Duration.ofMillis(100));
  }

  IdeServerService(
      IdeService ide,
      ProcessStarter starter,
      PortAllocator ports,
      Duration startTimeout,
      Duration pollInterval) {
    this.ide = Objects.requireNonNull(ide, "ide");
    this.starter = Objects.requireNonNull(starter, "starter");
    this.ports = Objects.requireNonNull(ports, "ports");
    this.startTimeout = Objects.requireNonNull(startTimeout, "startTimeout");
    this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
  }

  /**
   * Returns the IDE port for a workspace, spawning the server when needed.
   *
   * @param id the workspace
   * @param home the workspace home directory, used as the server working directory
   * @return the loopback port serving the IDE
   */
  public int ensureRunning(WorkspaceId id, Path home) {
    return ensureRunning(id, home, phase -> {});
  }

  /**
   * Returns the IDE port for a workspace, spawning the server when needed and reporting each
   * sub-step to the listener so callers can surface real boot progress.
   *
   * @param id the workspace
   * @param home the workspace home directory, used as the server working directory
   * @param progress receives EXTRACTING, SPAWNING, then WAITING in order; never called when an
   *     already-running server is reused
   * @return the loopback port serving the IDE
   */
  public int ensureRunning(
      WorkspaceId id, Path home, java.util.function.Consumer<BootPhase> progress) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(home, "home");
    Objects.requireNonNull(progress, "progress");
    RunningServer existing = servers.get(id.value());
    if (existing != null) {
      if (existing.process().isAlive()) {
        return existing.port();
      }
      servers.remove(id.value(), existing);
    }
    progress.accept(BootPhase.EXTRACTING);
    Path serverDir = ide.ensureExtracted();
    int port;
    try {
      port = ports.allocate();
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot allocate IDE port", failure);
    }
    Path logFile = home.resolve(".jahaziel-ide");
    Process process;
    try {
      progress.accept(BootPhase.SPAWNING);
      process =
          starter.start(
              List.of(
                  nodeBinary(serverDir).toString(),
                  serverDir.resolve("out/server-main.js").toString(),
                  "--host",
                  "127.0.0.1",
                  "--port",
                  String.valueOf(port),
                  "--server-data-dir",
                  logFile.toString(),
                  "--without-connection-token"),
              home);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot start IDE server", failure);
    }
    progress.accept(BootPhase.WAITING);
    waitForPort(port, process, home.resolve(".jahaziel-ide").resolve("server.log"));
    servers.put(id.value(), new RunningServer(port, process));
    return port;
  }

  /**
   * Stops the workspace IDE server, if one is tracked.
   *
   * @param id the workspace
   */
  public void stop(WorkspaceId id) {
    Objects.requireNonNull(id, "id");
    RunningServer running = servers.remove(id.value());
    if (running != null) {
      running.process().destroyForcibly();
    }
  }

  /** Stops every tracked IDE server. Called on context shutdown. */
  public void shutdownAll() {
    servers.forEach((id, running) -> running.process().destroyForcibly());
    servers.clear();
  }

  private void waitForPort(int port, Process process, Path logFile) {
    Instant deadline = Instant.now().plus(startTimeout);
    while (true) {
      if (!process.isAlive()) {
        throw new IllegalStateException(
            "IDE server exited during startup (exit="
                + process.exitValue()
                + ", log "
                + logFile
                + "): "
                + logTail(logFile));
      }
      try (Socket probe = new Socket()) {
        probe.connect(new InetSocketAddress("127.0.0.1", port), (int) pollInterval.toMillis());
        return;
      } catch (IOException notListening) {
        if (Instant.now().isAfter(deadline)) {
          process.destroyForcibly();
          throw new IllegalStateException("IDE server did not listen in time");
        }
        java.util.concurrent.locks.LockSupport.parkNanos(pollInterval.toNanos());
      }
    }
  }

  /**
   * Returns the tail of the IDE log for a startup failure, capped so the failure reason stays
   * readable on the workbench card.
   */
  private static String logTail(Path logFile) {
    try {
      List<String> lines = Files.readAllLines(logFile);
      List<String> tail = lines.subList(Math.max(0, lines.size() - 5), lines.size());
      String text = String.join(" | ", tail).trim();
      return text.substring(Math.max(0, text.length() - 400));
    } catch (IOException | RuntimeException unreadable) {
      return "unreadable";
    }
  }

  private static Path nodeBinary(Path serverDir) {
    Path bundled = serverDir.resolve("node.exe");
    if (Files.exists(bundled)) {
      return bundled;
    }
    return serverDir.resolve("node");
  }

  static Process launch(List<String> command, Path workDir) throws IOException {
    Path logFile = workDir.resolve(".jahaziel-ide").resolve("server.log");
    Files.createDirectories(logFile.getParent());
    return new ProcessBuilder(command)
        .directory(workDir.toFile())
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
        .redirectErrorStream(true)
        .start();
  }

  static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
