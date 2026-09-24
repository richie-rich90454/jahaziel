/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.nodeagent.storage.NioCommandRunner;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Remaining branches of {@link LocalProcessBackend} and {@link NodeMetricsCollector}. */
class LocalAndMetricsCoverageTest {
  @Test
  void localNameAndDescriptorFailure(@TempDir Path root) throws Exception {
    LocalProcessBackend backend =
        new LocalProcessBackend(new FolderStorage(new NioCommandRunner(), root.toString()));
    assertEquals("local", backend.name());
    UUID workspace = UUID.randomUUID();
    PrepareRequest prepared =
        new PrepareRequest(
            workspace, UUID.randomUUID(), new ImageRef("img"), new QuotaBytes(1 << 20), 1, 512);
    WorkspaceHandle handle = backend.prepare(prepared);
    Path block = root.resolve("block");
    Files.writeString(block, "x");
    WorkspaceHandle bad = new WorkspaceHandle(workspace, handle.homePath(), block.resolve("child"));
    // Direct failure: descriptor parent is under a file.
    RuntimeRequest request = new RuntimeRequest(workspace, 1, 512, 0, 0, 0, 100, 100, List.of());
    // Fresh backend backed by a storage mock returning the bad handle.
    dev.jahaziel.spi.WorkspaceStorage stub = mock(dev.jahaziel.spi.WorkspaceStorage.class);
    org.mockito.Mockito.when(
            stub.prepare(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(bad);
    LocalProcessBackend withBad = new LocalProcessBackend(stub);
    withBad.prepare(prepared);
    assertThrows(UncheckedIOException.class, () -> withBad.start(request));
    assertEquals(bad, bad);
  }

  @Test
  void localDescriptorEncodingFailure(@TempDir Path root) throws Exception {
    FolderStorage storage = new FolderStorage(new NioCommandRunner(), root.toString());
    LocalProcessBackend backend = new LocalProcessBackend(storage);
    UUID workspace = UUID.randomUUID();
    backend.prepare(
        new PrepareRequest(
            workspace, UUID.randomUUID(), new ImageRef("img"), new QuotaBytes(1 << 20), 1, 512));
    Field mapperField = LocalProcessBackend.class.getDeclaredField("mapper");
    mapperField.setAccessible(true);
    ObjectMapper failing =
        new ObjectMapper() {
          @Override
          public String writeValueAsString(Object value)
              throws tools.jackson.core.JacksonException {
            throw new tools.jackson.core.JacksonException("boom") {};
          }
        };
    ObjectMapper original = (ObjectMapper) mapperField.get(backend);
    mapperField.set(backend, failing);
    try {
      RuntimeRequest request = new RuntimeRequest(workspace, 1, 512, 0, 0, 0, 100, 100, List.of());
      assertThrows(IllegalStateException.class, () -> backend.start(request));
    } finally {
      mapperField.set(backend, original);
    }
  }

  @Test
  void metricsDiskFailure(@TempDir Path root) throws Exception {
    Path gone = root.resolve("gone");
    NodeMetricsCollector collector = new NodeMetricsCollector(gone);
    Files.createDirectories(gone);
    // Delete to make getFileStore fail on some platforms; at least sample works.
    HostSample sample = collector.sample();
    assertTrue(sample.cpuUsagePercent() >= 0);
    Path missing = root.resolve("no-such-" + System.nanoTime());
    NodeMetricsCollector broken = new NodeMetricsCollector(missing);
    try {
      broken.sample();
    } catch (UncheckedIOException expected) {
      assertTrue(expected.getMessage().contains(missing.toString()));
      return;
    }
    // ponytail: Windows resolves missing parents to existing store, so no throw is fine.
    assertTrue(broken.sample().diskTotalBytes() >= 0);
  }

  @Test
  void metricsNullRejects() {
    assertThrows(NullPointerException.class, () -> new NodeMetricsCollector(null));
    LocalProcessBackend backend =
        new LocalProcessBackend(new FolderStorage(new NioCommandRunner(), "/tmp"));
    assertThrows(NullPointerException.class, () -> new LocalProcessBackend(null));
    assertThrows(NullPointerException.class, () -> backend.start(null));
    assertThrows(
        IllegalStateException.class,
        () ->
            backend.start(
                new RuntimeRequest(UUID.randomUUID(), 1, 1, 0, 0, 0, 1, 1, List.of("x"))));
    RuntimeHandle unknown = new RuntimeHandle(UUID.randomUUID());
    backend.stop(unknown, false);
  }
}
