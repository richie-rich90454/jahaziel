/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Firecracker backend. Linux only, requires {@code /dev/kvm}. Selected with {@code
 * jahaziel.workspace.backend=firecracker}; anything else falls back to {@code local}.
 */
public final class FirecrackerBackend implements NodeBackend {
  private static final String BINARY = "/usr/bin/firecracker";

  private final Path workspacesDir;
  private final ConcurrentMap<UUID, Process> processes = new ConcurrentHashMap<>();

  public FirecrackerBackend(Path workspacesDir) {
    this.workspacesDir = Objects.requireNonNull(workspacesDir, "workspacesDir");
  }

  /** Returns true on Linux when {@code /dev/kvm} exists. */
  public static boolean kvmAvailable() {
    return Platforms.detect(null).os() == Platform.Os.LINUX && Files.exists(Path.of("/dev/kvm"));
  }

  @Override
  public String name() {
    return "firecracker";
  }

  @Override
  public WorkspaceHandle prepare(PrepareRequest request) {
    requireKvm();
    Path jail = dir(request.workspaceId());
    Path home = jail.resolve("home");
    try {
      Files.createDirectories(home);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return new WorkspaceHandle(request.workspaceId(), home, jail);
  }

  @Override
  public RuntimeHandle start(RuntimeRequest request) {
    requireKvm();
    if (!Files.isExecutable(Path.of(BINARY))) {
      throw new IllegalStateException("firecracker binary not found: " + BINARY);
    }
    stop(new RuntimeHandle(request.workspaceId()), true);
    Path jail = dir(request.workspaceId());
    try {
      Files.createDirectories(jail);
      Process process =
          new ProcessBuilder(BINARY, "--api-sock", jail.resolve("firecracker.sock").toString())
              .directory(jail.toFile())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      processes.put(request.workspaceId(), process);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return new RuntimeHandle(request.workspaceId());
  }

  @Override
  public void stop(RuntimeHandle handle, boolean graceful) {
    Process process = processes.remove(handle.value());
    if (process == null) {
      return;
    }
    process.descendants().forEach(descendant -> descendant.destroy());
    if (graceful) {
      process.destroy();
    } else {
      process.destroyForcibly();
    }
  }

  @Override
  public void destroy(WorkspaceHandle handle) {
    stop(new RuntimeHandle(handle.workspaceId()), true);
  }

  private Path dir(UUID workspaceId) {
    return workspacesDir.resolve(workspaceId.toString());
  }

  private static void requireKvm() {
    if (!kvmAvailable()) {
      throw new IllegalStateException("firecracker backend requires Linux with /dev/kvm");
    }
  }
}
