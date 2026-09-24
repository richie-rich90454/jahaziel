/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.nodeagent.service.HostSample;
import dev.jahaziel.nodeagent.service.NodeMetricsCollector;
import dev.jahaziel.nodeagent.sync.SyncServer;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** Final gap tests: MXBean fallbacks, POSIX chmod, and sync persistence paths. */
class NodeAgentFinalTest {
  @TempDir private Path tmp;

  @Test
  void sampleWithPlainBeanAndNoLoad() {
    OperatingSystemMXBean plain = mock(OperatingSystemMXBean.class);
    when(plain.getSystemLoadAverage()).thenReturn(-1.0);
    try (var factory = Mockito.mockStatic(ManagementFactory.class)) {
      factory.when(ManagementFactory::getOperatingSystemMXBean).thenReturn(plain);
      HostSample sample = new NodeMetricsCollector(tmp).sample();
      assertEquals(0, sample.memoryUsedBytes());
      assertEquals(0, sample.memoryTotalBytes());
      assertEquals(0, sample.loadAverage1m());
    }
  }

  @Test
  void sampleReportsPositiveLoad() {
    OperatingSystemMXBean plain = mock(OperatingSystemMXBean.class);
    when(plain.getSystemLoadAverage()).thenReturn(2.5);
    try (var factory = Mockito.mockStatic(ManagementFactory.class)) {
      factory.when(ManagementFactory::getOperatingSystemMXBean).thenReturn(plain);
      HostSample sample = new NodeMetricsCollector(tmp).sample();
      assertEquals(2.5, sample.loadAverage1m());
    }
  }

  private static void chmod(Path target, String mode) throws Exception {
    Method chmod = NioCommandRunner.class.getDeclaredMethod("chmod", Path.class, String.class);
    chmod.setAccessible(true);
    try {
      chmod.invoke(null, target, mode);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      throw (Exception) failure.getCause();
    }
  }

  @Test
  void chmodFallsBackOnWindows() throws Exception {
    Path dir = tmp.resolve("data");
    Files.createDirectories(dir);
    Path file = dir.resolve("f.txt");
    Files.writeString(file, "x");
    chmod(dir, "u+w");
    chmod(dir, "a-w");
    assertTrue(Files.isRegularFile(file));
  }

  @Test
  void chmodRejectsUnknownMode() {
    assertThrows(
        dev.jahaziel.nodeagent.storage.StorageException.class,
        () -> chmod(tmp.resolve("nope"), "+w"));
  }

  @Test
  void chmodAppliesPosixModes() throws Exception {
    Path dir = tmp.resolve("posix");
    Files.createDirectories(dir);
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files
          .when(() -> Files.getPosixFilePermissions(any(), any()))
          .thenReturn(EnumSet.of(PosixFilePermission.OWNER_READ));
      files.when(() -> Files.setPosixFilePermissions(any(), any())).thenAnswer(inv -> null);
      chmod(dir, "u+w");
      chmod(dir, "a-w");
    }
  }

  @Test
  void deleteRecursiveHandlesMissingAndDangling() throws Exception {
    NioCommandRunner.deleteRecursive(tmp.resolve("missing-dir"));
    Path dangling = tmp.resolve("dangling");
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.exists(dangling)).thenReturn(false);
      files.when(() -> Files.isSymbolicLink(dangling)).thenReturn(true);
      assertThrows(StorageException.class, () -> NioCommandRunner.deleteRecursive(dangling));
    }
  }

  @Test
  void deleteRecursiveReportsIterationFailure() throws Exception {
    Path dir = tmp.resolve("iter");
    Files.createDirectories(dir);
    java.nio.file.DirectoryStream<Path> stream = mock(java.nio.file.DirectoryStream.class);
    java.util.Iterator<Path> iterator = mock(java.util.Iterator.class);
    when(stream.iterator()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(true);
    when(iterator.next())
        .thenThrow(new java.nio.file.DirectoryIteratorException(new IOException("boom")));
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.newDirectoryStream(any())).thenReturn(stream);
      assertThrows(StorageException.class, () -> NioCommandRunner.deleteRecursive(dir));
    }
  }

  @Test
  void deleteRecursiveReportsTreeFailure() throws Exception {
    Path dir = tmp.resolve("tree");
    Files.createDirectories(dir);
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files
          .when(
              () ->
                  Files.walkFileTree(
                      Mockito.<Path>any(), Mockito.<java.nio.file.FileVisitor<Path>>any()))
          .thenThrow(new IOException("no walk"));
      assertThrows(StorageException.class, () -> NioCommandRunner.deleteRecursive(dir));
    }
  }

  @Test
  void sizeOfSkipsUnreadableFile() throws Exception {
    Path dir = tmp.resolve("sized2");
    Files.createDirectories(dir);
    Path file = dir.resolve("f.txt");
    Files.writeString(file, "hello");
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.size(file)).thenThrow(new IOException("deny"));
      assertEquals(0, NioCommandRunner.sizeOf(dir));
    }
    assertEquals(5, NioCommandRunner.sizeOf(dir));
  }

  @Test
  void copyContentsStripsDotSlashSuffix() throws Exception {
    Path source = tmp.resolve("srcdir");
    Files.createDirectories(source);
    Files.writeString(source.resolve("a.txt"), "a");
    Path dest = tmp.resolve("destdir");
    // Windows Path.toString never ends with "/.", so fake the spelling with a mock.
    Path suffixed = mock(Path.class);
    when(suffixed.toString()).thenReturn("/x/.");
    Method copy =
        NioCommandRunner.class.getDeclaredMethod("copyContentsOf", Path.class, Path.class);
    copy.setAccessible(true);
    try {
      copy.invoke(null, suffixed, dest);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      throw (Exception) failure.getCause();
    }
    assertTrue(Files.notExists(dest.resolve("a.txt")));
    try {
      copy.invoke(null, source, tmp.resolve("destdir2"));
    } catch (java.lang.reflect.InvocationTargetException failure) {
      throw (Exception) failure.getCause();
    }
    assertTrue(Files.isRegularFile(tmp.resolve("destdir2").resolve("a.txt")));
  }

  @Test
  void sizeOfReportsWalkFailure() throws Exception {
    Path dir = tmp.resolve("sized");
    Files.createDirectories(dir);
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.walk(any(Path.class))).thenThrow(new IOException("no walk"));
      assertThrows(StorageException.class, () -> NioCommandRunner.sizeOf(dir));
    }
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  @Test
  void acceptLoopReturnsWhenNeverStarted() throws Exception {
    Path state = tmp.resolve("never");
    Files.createDirectories(state);
    SyncServer server = new SyncServer(freePort(), state);
    Method acceptLoop = SyncServer.class.getDeclaredMethod("acceptLoop");
    acceptLoop.setAccessible(true);
    acceptLoop.invoke(server);
  }

  @Test
  void acceptLoopReturnsWhenClosed() throws Exception {
    Path state = tmp.resolve("state");
    Files.createDirectories(state);
    SyncServer server = new SyncServer(freePort(), state);
    server.start();
    server.stop();
    Method acceptLoop = SyncServer.class.getDeclaredMethod("acceptLoop");
    acceptLoop.setAccessible(true);
    acceptLoop.invoke(server);
  }

  @Test
  void registerPersistsThroughMoveFallback() throws Exception {
    Path state = tmp.resolve("persist");
    Files.createDirectories(state);
    Path home = tmp.resolve("home");
    Files.createDirectories(home);
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files
          .when(
              () ->
                  Files.move(
                      any(),
                      any(),
                      eq(java.nio.file.StandardCopyOption.ATOMIC_MOVE),
                      eq(java.nio.file.StandardCopyOption.REPLACE_EXISTING)))
          .thenThrow(new java.nio.file.AtomicMoveNotSupportedException("a", "b", "c"));
      SyncServer server = new SyncServer(freePort(), state);
      server.register(UUID.randomUUID(), home, 1024L);
      assertTrue(Files.isRegularFile(state.resolve("registry.properties")));
    }
  }

  @Test
  void restrictSucceedsWithPosix() throws Exception {
    Path state = tmp.resolve("posix-ok");
    Files.createDirectories(state);
    Path home = tmp.resolve("home3");
    Files.createDirectories(home);
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.setPosixFilePermissions(any(), any())).thenAnswer(inv -> null);
      SyncServer server = new SyncServer(freePort(), state);
      server.register(UUID.randomUUID(), home, 1024L);
      assertTrue(Files.isRegularFile(state.resolve("registry.properties")));
    }
  }

  @Test
  void restrictIgnoresIoFailure() throws Exception {
    Path state = tmp.resolve("restrict");
    Files.createDirectories(state);
    Path home = tmp.resolve("home2");
    Files.createDirectories(home);
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files
          .when(() -> Files.setPosixFilePermissions(any(), any()))
          .thenThrow(new IOException("deny"));
      SyncServer server = new SyncServer(freePort(), state);
      server.register(UUID.randomUUID(), home, 1024L);
    }
  }

  @Test
  void constructorReportsUnloadableRegistry() throws Exception {
    Path state = tmp.resolve("corrupt");
    Files.createDirectories(state);
    Files.writeString(state.resolve("registry.properties"), "k=v");
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.newInputStream(any())).thenThrow(new IOException("deny"));
      assertThrows(
          dev.jahaziel.nodeagent.sync.SyncException.class, () -> new SyncServer(freePort(), state));
    }
  }
}
