/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.nodeagent.cgroup.CgroupV2;
import dev.jahaziel.nodeagent.config.NodeAgentConfig;
import dev.jahaziel.nodeagent.firecracker.FirecrackerClient;
import dev.jahaziel.nodeagent.firecracker.FirecrackerException;
import dev.jahaziel.nodeagent.jail.Jailer;
import dev.jahaziel.nodeagent.storage.CommandRunner;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers {@link FirecrackerBackend} without Linux or KVM via mocks. */
class FirecrackerBackendTest {
  @TempDir private Path root;

  private FolderStorage storage;
  private FirecrackerClient firecracker;
  private Jailer jailer;
  private CgroupV2 cgroups;
  private CommandRunner commands;
  private NodeAgentConfig config;
  private FirecrackerBackend backend;
  private Path jailRoot;
  private Path imagesDir;
  private Path kernelFile;
  private Path imageFile;

  @BeforeEach
  void setUp() throws Exception {
    storage = mock(FolderStorage.class);
    firecracker = mock(FirecrackerClient.class);
    jailer = mock(Jailer.class);
    cgroups = mock(CgroupV2.class);
    commands = mock(CommandRunner.class);
    jailRoot = root.resolve("jail");
    imagesDir = root.resolve("images");
    Files.createDirectories(jailRoot);
    Files.createDirectories(imagesDir);
    kernelFile = root.resolve("vmlinux");
    Files.writeString(kernelFile, "kernel");
    imageFile = imagesDir.resolve("test-image.ext4");
    Files.writeString(imageFile, "rootfs");
    config =
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
    backend = new FirecrackerBackend(storage, firecracker, jailer, cgroups, commands, config);
    when(commands.run(any())).thenReturn("");
  }

  @Test
  void nameIsFirecracker() {
    assertEquals("firecracker", backend.name());
  }

  @Test
  void constructorRejectsNulls() {
    assertThrows(
        NullPointerException.class,
        () -> new FirecrackerBackend(null, firecracker, jailer, cgroups, commands, config));
    assertThrows(
        NullPointerException.class,
        () -> new FirecrackerBackend(storage, null, jailer, cgroups, commands, config));
    assertThrows(
        NullPointerException.class,
        () -> new FirecrackerBackend(storage, firecracker, null, cgroups, commands, config));
    assertThrows(
        NullPointerException.class,
        () -> new FirecrackerBackend(storage, firecracker, jailer, null, commands, config));
    assertThrows(
        NullPointerException.class,
        () -> new FirecrackerBackend(storage, firecracker, jailer, cgroups, null, config));
    assertThrows(
        NullPointerException.class,
        () -> new FirecrackerBackend(storage, firecracker, jailer, cgroups, commands, null));
  }

  @Test
  void prepareStagesAndWritesConfig() throws Exception {
    UUID workspace = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    Path home = root.resolve("home-" + workspace);
    Files.createDirectories(home);
    Path jail = jailRoot.resolve(workspace.toString());
    WorkspaceHandle handle = new WorkspaceHandle(workspace, home, jail);
    when(storage.prepare(any(), any(), any(), any())).thenReturn(handle);
    doNothing().when(cgroups).apply(any(), anyInt(), anyLong(), anyInt());
    PrepareRequest request =
        new PrepareRequest(
            workspace, owner, new ImageRef("test-image"), new QuotaBytes(1 << 20), 2, 2048);
    WorkspaceHandle out = backend.prepare(request);
    assertEquals(handle, out);
    verify(storage).prepare(any(), any(), any(), any());
    verify(cgroups).apply(workspace, 2, 2048, 4096);
    assertTrue(Files.isRegularFile(jail.resolve("root/firecracker.json")));
    assertThrows(NullPointerException.class, () -> backend.prepare(null));
  }

  @Test
  void prepareFailsWhenImageMissing() throws Exception {
    UUID workspace = UUID.randomUUID();
    Path home = root.resolve("home-missing");
    Files.createDirectories(home);
    Path jail = jailRoot.resolve(workspace.toString());
    WorkspaceHandle handle = new WorkspaceHandle(workspace, home, jail);
    when(storage.prepare(any(), any(), any(), any())).thenReturn(handle);
    PrepareRequest request =
        new PrepareRequest(
            workspace,
            UUID.randomUUID(),
            new ImageRef("no-such-image"),
            new QuotaBytes(1 << 20),
            1,
            512);
    backend.prepare(request);
    RuntimeRequest runtime = new RuntimeRequest(workspace, 1, 512, 0, 0, 0, 100, 100, List.of());
    assertThrows(UncheckedIOException.class, () -> backend.start(runtime));
  }

  @Test
  void prepareFailsWhenJailNotWritable() throws Exception {
    Path fileBlock = root.resolve("block");
    Files.writeString(fileBlock, "x");
    UUID workspace = UUID.randomUUID();
    WorkspaceHandle handle = new WorkspaceHandle(workspace, root.resolve("h"), fileBlock);
    when(storage.prepare(any(), any(), any(), any())).thenReturn(handle);
    PrepareRequest request =
        new PrepareRequest(
            workspace,
            UUID.randomUUID(),
            new ImageRef("test-image"),
            new QuotaBytes(1 << 20),
            1,
            512);
    assertThrows(UncheckedIOException.class, () -> backend.prepare(request));
  }

  @Test
  void startBootsAndIncrementsIds() throws Exception {
    UUID workspace = UUID.randomUUID();
    Path home = root.resolve("home-start");
    Files.createDirectories(home);
    Path jail = jailRoot.resolve(workspace.toString());
    WorkspaceHandle handle = new WorkspaceHandle(workspace, home, jail);
    when(storage.prepare(any(), any(), any(), any())).thenReturn(handle);
    backend.prepare(
        new PrepareRequest(
            workspace,
            UUID.randomUUID(),
            new ImageRef("test-image"),
            new QuotaBytes(1 << 20),
            2,
            2048));
    Process proc = mock(Process.class);
    when(jailer.start(any(), anyInt(), anyInt(), anyString())).thenReturn(proc);
    RuntimeRequest first = new RuntimeRequest(workspace, 2, 2048, 0, 0, 0, 5000, 4096, List.of());
    RuntimeHandle out = backend.start(first);
    assertEquals(workspace, out.value());
    assertTrue(Files.exists(jail.resolve("root/rootfs.img")));
    assertTrue(Files.exists(jail.resolve("root/kernel")));
    verify(commands).run(List.of("ip", "netns", "add", "jahaziel-" + workspace));
    verify(jailer).start(any(), anyInt(), anyInt(), anyString());
    verify(firecracker)
        .boot(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyInt(),
            anyLong(),
            anyString(),
            any());
    RuntimeRequest second =
        new RuntimeRequest(workspace, 1, 512, 0, 0, 0, 100, 100, List.of("k=v"));
    backend.start(second);
    assertThrows(NullPointerException.class, () -> backend.start(null));
  }

  @Test
  void startWithoutPrepareThrows() {
    UUID workspace = UUID.randomUUID();
    RuntimeRequest request = new RuntimeRequest(workspace, 2, 2048, 0, 0, 0, 5000, 4096, List.of());
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> backend.start(request));
    assertTrue(failure.getMessage().contains(workspace.toString()));
  }

  @Test
  void stopGracefulShutdownAndSwallow() {
    UUID workspace = UUID.randomUUID();
    RuntimeHandle handle = new RuntimeHandle(workspace);
    backend.stop(handle, true);
    verify(firecracker).shutdown(anyString());
    doThrow(new FirecrackerException("/actions", 500, "gone"))
        .when(firecracker)
        .shutdown(anyString());
    backend.stop(handle, true);
    backend.stop(handle, false);
    assertThrows(NullPointerException.class, () -> backend.stop(null, true));
  }

  @Test
  void destroyRemovesNetnsAndStorage() {
    UUID workspace = UUID.randomUUID();
    Path home = root.resolve("home-destroy");
    Path jail = jailRoot.resolve(workspace.toString());
    WorkspaceHandle handle = new WorkspaceHandle(workspace, home, jail);
    backend.destroy(handle);
    verify(commands).run(List.of("ip", "netns", "delete", "jahaziel-" + workspace));
    verify(storage).destroy(handle);
    assertThrows(NullPointerException.class, () -> backend.destroy(null));
  }
}
