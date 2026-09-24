/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.nodeagent.cgroup.CgroupV2;
import dev.jahaziel.nodeagent.config.NodeAgentConfig;
import dev.jahaziel.nodeagent.firecracker.FirecrackerClient;
import dev.jahaziel.nodeagent.jail.Jailer;
import dev.jahaziel.nodeagent.storage.CommandRunner;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.WorkspaceHandle;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** The config-encoding failure branch of {@link FirecrackerBackend}. */
class FirecrackerBackendCoverageGapTest {
  @Test
  void writeFirecrackerConfigEncodingFailure(@TempDir Path root) throws Exception {
    Path jailRoot = root.resolve("jail");
    Path imagesDir = root.resolve("images");
    Files.createDirectories(jailRoot);
    Files.createDirectories(imagesDir);
    Path kernelFile = root.resolve("vmlinux");
    Files.writeString(kernelFile, "kernel");
    Files.writeString(imagesDir.resolve("test-image.ext4"), "rootfs");
    NodeAgentConfig config =
        new NodeAgentConfig(
            "node-a",
            jailRoot.toString(),
            root.resolve("firecracker").toString(),
            root.resolve("jailer").toString(),
            kernelFile.toString(),
            root.resolve("store").toString(),
            root.resolve("store/workspaces").toString(),
            imagesDir.toString(),
            root.resolve("store/audit").toString(),
            "br-jahaziel",
            "tap-",
            "10.100.0.0/16",
            root.resolve("cgroup").toString());
    FolderStorage storage = mock(FolderStorage.class);
    FirecrackerBackend backend =
        new FirecrackerBackend(
            storage,
            mock(FirecrackerClient.class),
            mock(Jailer.class),
            mock(CgroupV2.class),
            mock(CommandRunner.class),
            config);
    UUID workspace = UUID.randomUUID();
    Path home = root.resolve("home");
    Files.createDirectories(home);
    WorkspaceHandle handle =
        new WorkspaceHandle(workspace, home, jailRoot.resolve(workspace.toString()));
    when(storage.prepare(any(), any(), any(), any())).thenReturn(handle);
    Field mapperField = FirecrackerBackend.class.getDeclaredField("mapper");
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
      PrepareRequest request =
          new PrepareRequest(
              workspace,
              UUID.randomUUID(),
              new ImageRef("test-image"),
              new QuotaBytes(1 << 20),
              2,
              2048);
      IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> backend.prepare(request));
      assertEquals("cannot encode firecracker config", failure.getMessage());
    } finally {
      mapperField.set(backend, original);
    }
  }
}
