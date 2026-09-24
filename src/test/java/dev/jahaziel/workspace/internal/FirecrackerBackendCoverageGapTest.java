/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.io.File;
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

class FirecrackerBackendCoverageGapTest {
  private PrepareRequest prepareFor(UUID id) {
    return new PrepareRequest(
        id, UUID.randomUUID(), new ImageRef("base:1"), new QuotaBytes(1 << 20), 1, 512);
  }

  private RuntimeRequest runtimeFor(UUID id) {
    return new RuntimeRequest(id, 1, 512, 0, 0, 0, 100, 100, List.of());
  }

  private void withLinux(Runnable body) {
    String oldOs = System.getProperty("os.name");
    String oldArch = System.getProperty("os.arch");
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    try {
      body.run();
    } finally {
      if (oldOs != null) {
        System.setProperty("os.name", oldOs);
      }
      if (oldArch != null) {
        System.setProperty("os.arch", oldArch);
      }
    }
  }

  @Test
  void ctorAndName(@TempDir Path root) {
    assertThrows(NullPointerException.class, () -> new FirecrackerBackend(null));
    assertEquals("firecracker", new FirecrackerBackend(root).name());
  }

  @Test
  void kvmAvailableBranches() {
    assertFalse(FirecrackerBackend.kvmAvailable());
    withLinux(
        () -> {
          try (MockedStatic<Files> files =
              Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.exists(any(Path.class))).thenReturn(false);
            assertFalse(FirecrackerBackend.kvmAvailable());
            files.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            assertTrue(FirecrackerBackend.kvmAvailable());
          }
        });
  }

  @Test
  void prepareRequiresKvm(@TempDir Path root) {
    FirecrackerBackend backend = new FirecrackerBackend(root);
    assertThrows(IllegalStateException.class, () -> backend.prepare(prepareFor(UUID.randomUUID())));
    assertThrows(IllegalStateException.class, () -> backend.start(runtimeFor(UUID.randomUUID())));
  }

  @Test
  void prepareSuccessAndIoFailure(@TempDir Path root) {
    withLinux(
        () -> {
          try (MockedStatic<Files> files =
              Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            FirecrackerBackend backend = new FirecrackerBackend(root);
            UUID id = UUID.randomUUID();
            WorkspaceHandle handle = backend.prepare(prepareFor(id));
            assertEquals(id, handle.workspaceId());
            files
                .when(() -> Files.createDirectories(any(Path.class)))
                .thenThrow(new IOException("boom"));
            assertThrows(
                UncheckedIOException.class, () -> backend.prepare(prepareFor(UUID.randomUUID())));
          }
        });
  }

  @Test
  void startRequiresBinary(@TempDir Path root) {
    withLinux(
        () -> {
          try (MockedStatic<Files> files =
              Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            files.when(() -> Files.isExecutable(any(Path.class))).thenReturn(false);
            FirecrackerBackend backend = new FirecrackerBackend(root);
            assertThrows(
                IllegalStateException.class, () -> backend.start(runtimeFor(UUID.randomUUID())));
          }
        });
  }

  @Test
  void startSuccessAndIoFailure(@TempDir Path root) {
    withLinux(
        () -> {
          try (MockedStatic<Files> files =
              Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            files.when(() -> Files.isExecutable(any(Path.class))).thenReturn(true);
            FirecrackerBackend backend = new FirecrackerBackend(root);
            UUID id = UUID.randomUUID();
            Process fake = mock(Process.class);
            try (MockedConstruction<ProcessBuilder> ignored =
                Mockito.mockConstruction(
                    ProcessBuilder.class,
                    (mock, ctx) -> {
                      try {
                        when(mock.directory(any(File.class))).thenReturn(mock);
                        when(mock.redirectOutput(any(ProcessBuilder.Redirect.class)))
                            .thenReturn(mock);
                        when(mock.redirectError(any(ProcessBuilder.Redirect.class)))
                            .thenReturn(mock);
                        when(mock.start()).thenReturn(fake);
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    })) {
              backend.prepare(prepareFor(id));
              assertEquals(id, backend.start(runtimeFor(id)).value());
            }
            try (MockedConstruction<ProcessBuilder> ignored =
                Mockito.mockConstruction(
                    ProcessBuilder.class,
                    (mock, ctx) -> {
                      try {
                        when(mock.directory(any(File.class))).thenReturn(mock);
                        when(mock.redirectOutput(any(ProcessBuilder.Redirect.class)))
                            .thenReturn(mock);
                        when(mock.redirectError(any(ProcessBuilder.Redirect.class)))
                            .thenReturn(mock);
                        when(mock.start()).thenThrow(new IOException("boom"));
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    })) {
              assertThrows(UncheckedIOException.class, () -> backend.start(runtimeFor(id)));
            }
          }
        });
  }

  @Test
  void stopBranches(@TempDir Path root) {
    FirecrackerBackend backend = new FirecrackerBackend(root);
    UUID id = UUID.randomUUID();
    backend.stop(new RuntimeHandle(id), true);
    Process fake = mock(Process.class);
    when(fake.descendants()).thenAnswer(inv -> java.util.stream.Stream.empty());
    withLinux(
        () -> {
          try (MockedStatic<Files> files =
                  Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
              MockedConstruction<ProcessBuilder> ignored =
                  Mockito.mockConstruction(
                      ProcessBuilder.class,
                      (mock, ctx) -> {
                        try {
                          when(mock.directory(any(File.class))).thenReturn(mock);
                          when(mock.redirectOutput(any(ProcessBuilder.Redirect.class)))
                              .thenReturn(mock);
                          when(mock.redirectError(any(ProcessBuilder.Redirect.class)))
                              .thenReturn(mock);
                          when(mock.start()).thenReturn(fake);
                        } catch (IOException e) {
                          throw new RuntimeException(e);
                        }
                      })) {
            files.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            files.when(() -> Files.isExecutable(any(Path.class))).thenReturn(true);
            backend.prepare(prepareFor(id));
            backend.start(runtimeFor(id));
          }
        });
    backend.stop(new RuntimeHandle(id), true);
    Mockito.verify(fake).destroy();
    withLinux(
        () -> {
          try (MockedStatic<Files> files =
                  Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
              MockedConstruction<ProcessBuilder> ignored =
                  Mockito.mockConstruction(
                      ProcessBuilder.class,
                      (mock, ctx) -> {
                        try {
                          when(mock.directory(any(File.class))).thenReturn(mock);
                          when(mock.redirectOutput(any(ProcessBuilder.Redirect.class)))
                              .thenReturn(mock);
                          when(mock.redirectError(any(ProcessBuilder.Redirect.class)))
                              .thenReturn(mock);
                          when(mock.start()).thenReturn(fake);
                        } catch (IOException e) {
                          throw new RuntimeException(e);
                        }
                      })) {
            files.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            files.when(() -> Files.isExecutable(any(Path.class))).thenReturn(true);
            backend.prepare(prepareFor(id));
            backend.start(runtimeFor(id));
          }
        });
    backend.stop(new RuntimeHandle(id), false);
    Mockito.verify(fake).destroyForcibly();
  }

  @Test
  void destroyStops(@TempDir Path root) {
    FirecrackerBackend backend = new FirecrackerBackend(root);
    UUID id = UUID.randomUUID();
    backend.destroy(new WorkspaceHandle(id, root.resolve("h"), root.resolve("j")));
    backend.stop(new RuntimeHandle(id), true);
  }
}
