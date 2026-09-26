/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ide.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.ide.internal.IdeServerService.BootPhase;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link IdeServerService} with fake processes and loopback sockets. */
class IdeServerServiceTest {
  @TempDir private Path tmp;

  private IdeService ideReturning(Path serverDir) {
    IdeService ide = mock(IdeService.class);
    when(ide.ensureExtracted()).thenReturn(serverDir);
    return ide;
  }

  private static int heldPort(ServerSocket socket) {
    return socket.getLocalPort();
  }

  @Test
  void ensureRunningSpawnsOnceAndReusesWhileAlive() throws Exception {
    Path serverDir = Files.createDirectory(tmp.resolve("ide"));
    AtomicReference<List<String>> command = new AtomicReference<>();
    AtomicReference<Path> workDir = new AtomicReference<>();
    AtomicReference<Path> nodeUsed = new AtomicReference<>();
    Process process = mock(Process.class);
    when(process.isAlive()).thenReturn(true);
    AtomicReference<Integer> spawns = new AtomicReference<>(0);
    try (ServerSocket listener = new ServerSocket(0)) {
      int port = heldPort(listener);
      IdeServerService service =
          new IdeServerService(
              ideReturning(serverDir),
              (argv, dir) -> {
                spawns.set(spawns.get() + 1);
                command.set(argv);
                workDir.set(dir);
                nodeUsed.set(Path.of(argv.get(0)));
                return process;
              },
              () -> port,
              Duration.ofSeconds(5),
              Duration.ofMillis(5));
      Path home = Files.createDirectory(tmp.resolve("home"));
      WorkspaceId id = new WorkspaceId(UUID.randomUUID());
      assertEquals(port, service.ensureRunning(id, home));
      assertEquals(port, service.ensureRunning(id, home));
      assertEquals(1, spawns.get());
      assertTrue(command.get().contains(serverDir.resolve("out/server-main.js").toString()));
      assertTrue(command.get().contains(String.valueOf(port)));
      assertEquals(home, workDir.get());
      assertEquals(serverDir.resolve("node"), nodeUsed.get());
    }
  }

  @Test
  void ensureRunningPrefersBundledNodeBinary() throws Exception {
    Path serverDir = Files.createDirectory(tmp.resolve("ide2"));
    Files.createFile(serverDir.resolve("node.exe"));
    AtomicReference<List<String>> command = new AtomicReference<>();
    Process process = mock(Process.class);
    when(process.isAlive()).thenReturn(true);
    try (ServerSocket listener = new ServerSocket(0)) {
      int port = heldPort(listener);
      IdeServerService service =
          new IdeServerService(
              ideReturning(serverDir),
              (argv, dir) -> {
                command.set(argv);
                return process;
              },
              () -> port,
              Duration.ofSeconds(5),
              Duration.ofMillis(5));
      Path home = Files.createDirectory(tmp.resolve("home2"));
      WorkspaceId id = new WorkspaceId(UUID.randomUUID());
      assertEquals(port, service.ensureRunning(id, home));
      assertEquals(serverDir.resolve("node.exe").toString(), command.get().get(0));
    }
  }

  @Test
  void ensureRunningRespawnsDeadTrackedServer() throws Exception {
    Path serverDir = Files.createDirectory(tmp.resolve("ide3"));
    AtomicReference<Boolean> alive = new AtomicReference<>(true);
    Process process = mock(Process.class);
    when(process.isAlive()).thenAnswer(ignored -> alive.get());
    Process replacement = mock(Process.class);
    when(replacement.isAlive()).thenReturn(true);
    java.util.Queue<Process> processes = new java.util.ArrayDeque<>(List.of(process, replacement));
    try (ServerSocket listener = new ServerSocket(0)) {
      int port = heldPort(listener);
      IdeServerService service =
          new IdeServerService(
              ideReturning(serverDir),
              (argv, dir) -> processes.remove(),
              () -> port,
              Duration.ofSeconds(5),
              Duration.ofMillis(5));
      Path home = Files.createDirectory(tmp.resolve("home3"));
      WorkspaceId id = new WorkspaceId(UUID.randomUUID());
      assertEquals(port, service.ensureRunning(id, home));
      alive.set(false);
      assertEquals(port, service.ensureRunning(id, home));
      assertTrue(processes.isEmpty());
    }
  }

  @Test
  void ensureRunningFailsWhenAllocatorFails() {
    IdeServerService service =
        new IdeServerService(
            ideReturning(tmp),
            (argv, dir) -> mock(Process.class),
            () -> {
              throw new IOException("no ports");
            },
            Duration.ofSeconds(5),
            Duration.ofMillis(5));
    assertThrows(
        UncheckedIOException.class,
        () -> service.ensureRunning(new WorkspaceId(UUID.randomUUID()), tmp));
  }

  @Test
  void ensureRunningFailsWhenStarterFails() {
    IdeServerService service =
        new IdeServerService(
            ideReturning(tmp),
            (argv, dir) -> {
              throw new IOException("noexec");
            },
            () -> 19851,
            Duration.ofSeconds(5),
            Duration.ofMillis(5));
    assertThrows(
        UncheckedIOException.class,
        () -> service.ensureRunning(new WorkspaceId(UUID.randomUUID()), tmp));
  }

  @Test
  void ensureRunningFailsWhenServerExitsEarly() {
    Process exited = mock(Process.class);
    when(exited.isAlive()).thenReturn(false);
    IdeServerService service =
        new IdeServerService(
            ideReturning(tmp),
            (argv, dir) -> exited,
            () -> 19852,
            Duration.ofSeconds(5),
            Duration.ofMillis(5));
    assertThrows(
        IllegalStateException.class,
        () -> service.ensureRunning(new WorkspaceId(UUID.randomUUID()), tmp));
  }

  @Test
  void ensureRunningFailsOnTimeoutAndReaps() {
    Process stuck = mock(Process.class);
    when(stuck.isAlive()).thenReturn(true);
    IdeServerService service =
        new IdeServerService(
            ideReturning(tmp),
            (argv, dir) -> stuck,
            () -> 19853,
            Duration.ofMillis(60),
            Duration.ofMillis(5));
    assertThrows(
        IllegalStateException.class,
        () -> service.ensureRunning(new WorkspaceId(UUID.randomUUID()), tmp));
    verify(stuck).destroyForcibly();
  }

  @Test
  void stopReapsTrackedServerAndIgnoresUnknown() throws Exception {
    Path serverDir = Files.createDirectory(tmp.resolve("ide4"));
    Process process = mock(Process.class);
    when(process.isAlive()).thenReturn(true);
    try (ServerSocket listener = new ServerSocket(0)) {
      int port = heldPort(listener);
      IdeServerService service =
          new IdeServerService(
              ideReturning(serverDir),
              (argv, dir) -> process,
              () -> port,
              Duration.ofSeconds(5),
              Duration.ofMillis(5));
      Path home = Files.createDirectory(tmp.resolve("home4"));
      WorkspaceId id = new WorkspaceId(UUID.randomUUID());
      service.ensureRunning(id, home);
      service.stop(id);
      verify(process).destroyForcibly();
      service.stop(id);
      service.stop(new WorkspaceId(UUID.randomUUID()));
    }
  }

  @Test
  void shutdownAllReapsEverythingTracked() throws Exception {
    Path serverDir = Files.createDirectory(tmp.resolve("ide5"));
    Process process = mock(Process.class);
    when(process.isAlive()).thenReturn(true);
    try (ServerSocket listener = new ServerSocket(0)) {
      int port = heldPort(listener);
      IdeServerService service =
          new IdeServerService(
              ideReturning(serverDir),
              (argv, dir) -> process,
              () -> port,
              Duration.ofSeconds(5),
              Duration.ofMillis(5));
      Path home = Files.createDirectory(tmp.resolve("home6"));
      WorkspaceId id = new WorkspaceId(UUID.randomUUID());
      service.ensureRunning(id, home);
      service.shutdownAll();
      verify(process).destroyForcibly();
      service.shutdownAll();
    }
  }

  @Test
  void ensureRunningReportsPhasesInOrder() throws Exception {
    Path serverDir = Files.createDirectory(tmp.resolve("ide-phases"));
    List<BootPhase> reported = new java.util.concurrent.CopyOnWriteArrayList<>();
    Process process = mock(Process.class);
    when(process.isAlive()).thenReturn(true);
    try (ServerSocket listener = new ServerSocket(0)) {
      int port = heldPort(listener);
      IdeServerService service =
          new IdeServerService(
              ideReturning(serverDir),
              (argv, dir) -> process,
              () -> port,
              Duration.ofSeconds(5),
              Duration.ofMillis(5));
      Path home = Files.createDirectory(tmp.resolve("home-phases"));
      WorkspaceId id = new WorkspaceId(UUID.randomUUID());
      assertEquals(port, service.ensureRunning(id, home, reported::add));
      assertEquals(List.of(BootPhase.EXTRACTING, BootPhase.SPAWNING, BootPhase.WAITING), reported);
    }
  }

  @Test
  void ensureRunningListenerIsRequired() throws Exception {
    Path serverDir = Files.createDirectory(tmp.resolve("ide-listener"));
    IdeServerService service =
        new IdeServerService(
            ideReturning(serverDir),
            (argv, dir) -> mock(Process.class),
            () -> 19860,
            Duration.ofSeconds(5),
            Duration.ofMillis(5));
    Path home = Files.createDirectory(tmp.resolve("home-listener"));
    assertThrows(
        NullPointerException.class,
        () -> service.ensureRunning(new WorkspaceId(UUID.randomUUID()), home, null));
  }

  @Test
  void defaultStarterLaunchesRealProcesses() throws Exception {
    Process process = IdeServerService.launch(List.of("java", "-version"), tmp);
    try {
      assertEquals(0, process.waitFor());
    } finally {
      process.destroyForcibly();
    }
    assertTrue(Files.isDirectory(tmp.resolve(".jahaziel-ide")));
  }

  @Test
  void defaultStarterFailsOnUnwritableLogDir() throws Exception {
    Path home = Files.createDirectory(tmp.resolve("home5"));
    Files.createFile(home.resolve(".jahaziel-ide"));
    assertThrows(
        IOException.class, () -> IdeServerService.launch(List.of("java", "-version"), home));
  }

  @Test
  void defaultAllocatorReturnsBindablePort() throws Exception {
    int port = IdeServerService.freePort();
    assertTrue(port >= 1 && port <= 65535);
    try (ServerSocket socket = new ServerSocket(port)) {
      assertEquals(port, socket.getLocalPort());
    }
  }
}
