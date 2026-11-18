/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class NotebookRunnerTest {
  @TempDir Path temporary;

  @Test
  void persistsCellOrderUnderWorkspaceHome() {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    NotebookRunner runner = new NotebookRunner(temporary, Mockito.mock(WorkspaceTaskService.class));
    List<NotebookCell> cells = new ArrayList<NotebookCell>();
    cells.add(new NotebookCell("second", "print(2)"));
    cells.add(new NotebookCell("first", "print(1)"));

    runner.save(workspaceId, "demo", cells);
    List<NotebookCell> loaded = runner.load(workspaceId, "demo");

    assertEquals(2, loaded.size());
    assertEquals("second", loaded.get(0).id());
    assertEquals("print(1)", loaded.get(1).source());
  }

  @Test
  void runsOnlyThroughTaskApiAndClassifiesOutput() {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    WorkspaceTaskService tasks = Mockito.mock(WorkspaceTaskService.class);
    NotebookRunner runner = new NotebookRunner(temporary, tasks);
    runner.save(workspaceId, "demo", Arrays.asList(new NotebookCell("cell", "print(1)")));

    UUID runId = UUID.randomUUID();
    Mockito.when(tasks.startTask(workspaceId, "json-task"))
        .thenReturn(run(workspaceId, runId, "SUCCEEDED", "{\"answer\":42}"));
    NotebookExecution json = runner.run(workspaceId, "demo", "json-task");
    assertEquals(CellOutput.JSON, json.outputs().get(0).type());
    assertEquals(runId.toString(), json.runId());
    Mockito.verify(tasks).startTask(workspaceId, "json-task");

    Mockito.when(tasks.startTask(workspaceId, "text-task"))
        .thenReturn(run(workspaceId, UUID.randomUUID(), "SUCCEEDED", "plain answer"));
    assertEquals(CellOutput.TEXT, runner.run(workspaceId, "demo", "text-task")
        .outputs().get(0).type());

    Mockito.when(tasks.startTask(workspaceId, "image-task"))
        .thenReturn(run(workspaceId, UUID.randomUUID(), "SUCCEEDED", "data:image/png;base64,iVBOR"));
    assertEquals(CellOutput.IMAGE, runner.run(workspaceId, "demo", "image-task")
        .outputs().get(0).type());
  }

  private static WorkspaceTaskService.TaskRun run(
      WorkspaceId workspaceId, UUID runId, String status, String output) {
    Instant now = Instant.now();
    return new WorkspaceTaskService.TaskRun(
        runId, workspaceId.value(), "task", "Notebook", status, output, 0, now, now);
  }
}
