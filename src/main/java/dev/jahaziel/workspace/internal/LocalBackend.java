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
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Default backend. One long-lived child process per workspace, rooted at {@code
 * data/workspaces/<id>/}. No isolation: processes share the control-plane host user. Terminal
 * sessions from the terminal service attach to this process tree.
 */
public final class LocalBackend implements NodeBackend {
  private final Path workspacesDir;
  private final ConcurrentMap<UUID, Process> processes = new ConcurrentHashMap<>();

  public LocalBackend(Path workspacesDir) {
    this.workspacesDir = Objects.requireNonNull(workspacesDir, "workspacesDir");
  }

  @Override
  public String name() {
    return "local";
  }

  @Override
  public WorkspaceHandle prepare(PrepareRequest request) {
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
    stop(new RuntimeHandle(request.workspaceId()), true);
    Path jail = dir(request.workspaceId());
    try {
      Files.createDirectories(jail);
      ProcessBuilder builder =
          Platforms.detect(null).os() == Platform.Os.WINDOWS
              ? new ProcessBuilder("cmd.exe", "/c", "ping", "-t", "127.0.0.1")
              : new ProcessBuilder("sleep", "infinity");
      builder.directory(jail.toFile());
      builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      processes.put(request.workspaceId(), builder.start());
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
    Path jail = dir(handle.workspaceId());
    if (!Files.exists(jail)) {
      return;
    }
    try (Stream<Path> tree = Files.walk(jail)) {
      tree.sorted(Comparator.reverseOrder()).forEach(LocalBackend::deleteQuietly);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private Path dir(UUID workspaceId) {
    return workspacesDir.resolve(workspaceId.toString());
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best effort cleanup; the next prepare recreates the layout.
    }
  }
}
