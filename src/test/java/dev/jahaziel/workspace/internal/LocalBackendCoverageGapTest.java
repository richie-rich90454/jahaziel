/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class LocalBackendCoverageGapTest {
  private PrepareRequest prepareFor(UUID id) {
    return new PrepareRequest(
        id, UUID.randomUUID(), new ImageRef("base:1"), new QuotaBytes(1 << 20), 1, 512);
  }

  private RuntimeRequest runtimeFor(UUID id) {
    return new RuntimeRequest(id, 1, 512, 0, 0, 0, 100, 100, List.of());
  }

  @Test
  void ctorRejectsNull(@TempDir Path root) {
    assertThrows(NullPointerException.class, () -> new LocalBackend(null));
    assertEquals("local", new LocalBackend(root).name());
  }

  @Test
  void prepareCreatesHome(@TempDir Path root) {
    LocalBackend backend = new LocalBackend(root);
    UUID id = UUID.randomUUID();
    WorkspaceHandle handle = backend.prepare(prepareFor(id));
    assertEquals(id, handle.workspaceId());
    assertTrue(Files.isDirectory(handle.homePath()));
  }

  @Test
  void prepareIoFailure(@TempDir Path root) throws Exception {
    Path file = root.resolve("block");
    Files.writeString(file, "x");
    LocalBackend backend = new LocalBackend(file);
    assertThrows(UncheckedIOException.class, () -> backend.prepare(prepareFor(UUID.randomUUID())));
  }

  @Test
  void startBothOsBranches(@TempDir Path root) {
    LocalBackend backend = new LocalBackend(root);
    UUID id = UUID.randomUUID();
    Process fake = mock(Process.class);
    when(fake.descendants()).thenAnswer(inv -> java.util.stream.Stream.empty());
    try (MockedConstruction<ProcessBuilder> ignored =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, ctx) -> {
              try {
                when(mock.start()).thenReturn(fake);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })) {
      backend.prepare(prepareFor(id));
      assertEquals(id, backend.start(runtimeFor(id)).value());
    }
    String oldOs = System.getProperty("os.name");
    String oldArch = System.getProperty("os.arch");
    System.setProperty("os.name", oldOs.contains("Windows") ? "Linux" : "Windows 11");
    System.setProperty("os.arch", "amd64");
    try {
      UUID other = UUID.randomUUID();
      try (MockedConstruction<ProcessBuilder> ignored =
          Mockito.mockConstruction(
              ProcessBuilder.class,
              (mock, ctx) -> {
                try {
                  when(mock.start()).thenReturn(fake);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              })) {
        backend.prepare(prepareFor(other));
        assertEquals(other, backend.start(runtimeFor(other)).value());
      }
    } finally {
      if (oldOs != null) {
        System.setProperty("os.name", oldOs);
      }
      if (oldArch != null) {
        System.setProperty("os.arch", oldArch);
      }
    }
    backend.stop(new RuntimeHandle(id), true);
  }

  @Test
  void startIoFailure(@TempDir Path root) throws Exception {
    Path file = root.resolve("block");
    Files.writeString(file, "x");
    LocalBackend backend = new LocalBackend(file);
    UUID id = UUID.randomUUID();
    try (MockedConstruction<ProcessBuilder> ignored =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, ctx) -> {
              try {
                when(mock.start()).thenThrow(new IOException("boom"));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })) {
      assertThrows(UncheckedIOException.class, () -> backend.start(runtimeFor(id)));
    }
  }

  @Test
  void stopBranches(@TempDir Path root) {
    LocalBackend backend = new LocalBackend(root);
    UUID id = UUID.randomUUID();
    backend.stop(new RuntimeHandle(id), true);
    Process fake = mock(Process.class);
    when(fake.descendants()).thenAnswer(inv -> java.util.stream.Stream.empty());
    try (MockedConstruction<ProcessBuilder> ignored =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, ctx) -> {
              try {
                when(mock.start()).thenReturn(fake);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })) {
      backend.prepare(prepareFor(id));
      backend.start(runtimeFor(id));
    }
    backend.stop(new RuntimeHandle(id), true);
    Mockito.verify(fake).destroy();
    try (MockedConstruction<ProcessBuilder> ignored =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, ctx) -> {
              try {
                when(mock.start()).thenReturn(fake);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })) {
      backend.prepare(prepareFor(id));
      backend.start(runtimeFor(id));
    }
    backend.stop(new RuntimeHandle(id), false);
    Mockito.verify(fake).destroyForcibly();
  }

  @Test
  void destroyBranches(@TempDir Path root) throws Exception {
    LocalBackend backend = new LocalBackend(root);
    UUID missing = UUID.randomUUID();
    backend.destroy(
        new WorkspaceHandle(missing, root.resolve("h"), root.resolve(missing.toString())));
    UUID id = UUID.randomUUID();
    backend.prepare(prepareFor(id));
    Files.writeString(root.resolve(id.toString()).resolve("home").resolve("a.txt"), "x");
    backend.destroy(
        new WorkspaceHandle(
            id, root.resolve(id.toString()).resolve("home"), root.resolve(id.toString())));
    assertTrue(Files.notExists(root.resolve(id.toString())));
  }

  @Test
  void destroyWalkFailure(@TempDir Path root) throws Exception {
    LocalBackend backend = new LocalBackend(root);
    UUID id = UUID.randomUUID();
    backend.prepare(prepareFor(id));
    Path jail = root.resolve(id.toString());
    WorkspaceHandle handle = new WorkspaceHandle(id, jail.resolve("home"), jail);
    try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.walk(any(Path.class))).thenThrow(new IOException("boom"));
      assertThrows(UncheckedIOException.class, () -> backend.destroy(handle));
    }
  }

  @Test
  void deleteQuietlySwallows(@TempDir Path root) throws Exception {
    LocalBackend backend = new LocalBackend(root);
    UUID id = UUID.randomUUID();
    backend.prepare(prepareFor(id));
    Path jail = root.resolve(id.toString());
    WorkspaceHandle handle = new WorkspaceHandle(id, jail.resolve("home"), jail);
    try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(new IOException("locked"));
      backend.destroy(handle);
    }
  }
}
