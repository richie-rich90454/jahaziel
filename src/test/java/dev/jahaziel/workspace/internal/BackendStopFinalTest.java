/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.spi.RuntimeHandle;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Final gap tests: stopping with live descendants destroys the whole tree. */
class BackendStopFinalTest {
  @TempDir private Path tmp;

  @SuppressWarnings("unchecked")
  private static void plant(Object backend, UUID id, Process process) throws Exception {
    Field field = backend.getClass().getDeclaredField("processes");
    field.setAccessible(true);
    ((ConcurrentMap<UUID, Process>) field.get(backend)).put(id, process);
  }

  private static Process tree(ProcessHandle descendant) {
    Process fake = mock(Process.class);
    when(fake.descendants()).thenAnswer(inv -> Stream.of(descendant));
    return fake;
  }

  @Test
  void localStopDestroysDescendants() throws Exception {
    LocalBackend backend = new LocalBackend(tmp.resolve("ws"));
    UUID id = UUID.randomUUID();
    ProcessHandle descendant = mock(ProcessHandle.class);
    plant(backend, id, tree(descendant));
    backend.stop(new RuntimeHandle(id), true);
    verify(descendant).destroy();
  }

  @Test
  void localStopForceDestroys() throws Exception {
    LocalBackend backend = new LocalBackend(tmp.resolve("ws"));
    UUID id = UUID.randomUUID();
    Process fake = mock(Process.class);
    when(fake.descendants()).thenAnswer(inv -> Stream.of(mock(ProcessHandle.class)));
    plant(backend, id, fake);
    backend.stop(new RuntimeHandle(id), false);
    verify(fake).destroyForcibly();
  }

  @Test
  void firecrackerStopDestroysDescendants() throws Exception {
    FirecrackerBackend backend = new FirecrackerBackend(tmp.resolve("fc"));
    UUID id = UUID.randomUUID();
    ProcessHandle descendant = mock(ProcessHandle.class);
    plant(backend, id, tree(descendant));
    backend.stop(new RuntimeHandle(id), true);
    verify(descendant).destroy();
  }

  @Test
  void missingProcessIsNoop() throws Exception {
    new LocalBackend(tmp.resolve("ws")).stop(new RuntimeHandle(UUID.randomUUID()), true);
    new FirecrackerBackend(tmp.resolve("fc")).stop(new RuntimeHandle(UUID.randomUUID()), false);
  }
}
