/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores notebook cell order under the workspace home and executes a notebook through the
 * workspace-owned task service only. It never spawns a process itself.
 */
public final class NotebookRunner {
  private static final int MAX_POLLS = 100;

  private final Path homesRoot;
  private final WorkspaceTaskService tasks;

  public NotebookRunner(Path dataRoot, WorkspaceTaskService tasks) {
    if (dataRoot == null || tasks == null) {
      throw new IllegalArgumentException("dataRoot and tasks are required");
    }
    this.homesRoot = dataRoot.resolve("workspaces");
    this.tasks = tasks;
  }

  /** Persists cells and their order under the workspace home. */
  public void save(WorkspaceId workspaceId, String notebookId, List<NotebookCell> cells) {
    Path directory = notebookDirectory(workspaceId, notebookId);
    Path cellDirectory = directory.resolve("cells");
    try {
      Files.createDirectories(cellDirectory);
      StringBuilder order = new StringBuilder();
      for (NotebookCell cell : cells) {
        Files.write(
            cellDirectory.resolve(cell.id() + ".txt"),
            cell.source().getBytes(StandardCharsets.UTF_8));
        order.append(cell.id()).append('\n');
      }
      Files.write(directory.resolve("order.txt"), order.toString().getBytes(StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot save notebook", failure);
    }
  }

  /** Loads cells in their persisted order. */
  public List<NotebookCell> load(WorkspaceId workspaceId, String notebookId) {
    Path directory = notebookDirectory(workspaceId, notebookId);
    Path orderFile = directory.resolve("order.txt");
    if (!Files.isRegularFile(orderFile)) {
      return new ArrayList<NotebookCell>();
    }
    List<NotebookCell> cells = new ArrayList<NotebookCell>();
    Path cellDirectory = directory.resolve("cells");
    if (!Files.isDirectory(cellDirectory)) {
      return cells;
    }
    Path realCellDirectory = PathGuard.root(cellDirectory);
    try {
      List<String> lines = Files.readAllLines(orderFile, StandardCharsets.UTF_8);
      for (String line : lines) {
        String id = line.trim();
        if (id.isEmpty()) {
          continue;
        }
        Path source = PathGuard.confine(realCellDirectory, id + ".txt");
        cells.add(new NotebookCell(
            id, new String(Files.readAllBytes(source), StandardCharsets.UTF_8)));
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot load notebook", failure);
    }
    return cells;
  }

  /** Starts one declared task for the notebook and classifies its bounded output. */
  public NotebookExecution run(WorkspaceId workspaceId, String notebookId, String taskId) {
    if (taskId == null || taskId.trim().isEmpty()) {
      throw new IllegalArgumentException("taskId is required");
    }
    load(workspaceId, notebookId);
    WorkspaceTaskService.TaskRun started = tasks.startTask(workspaceId, taskId);
    WorkspaceTaskService.TaskRun current = started;
    int polls = 0;
    while (!isTerminal(current.status()) && polls < MAX_POLLS) {
      current = tasks.taskRun(current.id());
      polls++;
    }
    List<CellOutput> outputs = new ArrayList<CellOutput>();
    outputs.add(classify(current.output()));
    return new NotebookExecution(notebookId, started.id().toString(), outputs);
  }

  private Path notebookDirectory(WorkspaceId workspaceId, String notebookId) {
    if (workspaceId == null || notebookId == null || !notebookId.matches("[A-Za-z0-9_-]+")) {
      throw new IllegalArgumentException("workspaceId and a safe notebookId are required");
    }
    Path home = homesRoot.resolve(workspaceId.value().toString()).resolve("home");
    try {
      Files.createDirectories(home.resolve(".datatools").resolve("notebooks"));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot prepare workspace home", failure);
    }
    return PathGuard.confine(
        PathGuard.root(home), ".datatools/notebooks/" + notebookId);
  }

  private static boolean isTerminal(String status) {
    if (status == null) {
      return true;
    }
    return "SUCCEEDED".equalsIgnoreCase(status)
        || "SUCCESS".equalsIgnoreCase(status)
        || "FAILED".equalsIgnoreCase(status)
        || "ERROR".equalsIgnoreCase(status)
        || "STOPPED".equalsIgnoreCase(status)
        || "CANCELLED".equalsIgnoreCase(status)
        || "CANCELED".equalsIgnoreCase(status)
        || "COMPLETED".equalsIgnoreCase(status);
  }

  private static CellOutput classify(String output) {
    String value = output == null ? "" : output;
    String trimmed = value.trim();
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      return CellOutput.json(value);
    }
    if (trimmed.startsWith("data:image/") || trimmed.startsWith("iVBOR")
        || trimmed.startsWith("/9j/")) {
      return CellOutput.image(value, "image/png");
    }
    return CellOutput.text(value);
  }
}
