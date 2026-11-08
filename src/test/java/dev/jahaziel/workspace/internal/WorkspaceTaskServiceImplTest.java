/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class WorkspaceTaskServiceImplTest {
  @TempDir Path temp;

  @Test
  void exposesOnlyTaskLabelsAndRunsNoCommandDuringTemplateAssociation() throws Exception {
    Path templates = Files.createDirectories(temp.resolve("templates/node"));
    Files.writeString(
        templates.resolve("template.json"),
        """
        {"name":"node","runTasks":[{"id":"start","label":"Start server","command":["node","server.js"],"port":3000}]}
        """);
    WorkspaceTaskService service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            new ObjectMapper(),
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());

    service.initialize(id, "node");

    assertEquals("READY", service.setupStatus(id).status());
    assertEquals(
        List.of(new WorkspaceTaskService.Task("start", "Start server", 3000)), service.tasks(id));
    assertEquals("", service.setupStatus(id).output());
  }

  @Test
  void readsLanguagePacksDeclaredByTheWorkspaceTemplate() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/python"));
    Files.writeString(
        template.resolve("template.json"),
        """
        {"name":"python","languagePacks":["python"]}
        """);
    WorkspaceTaskService service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            new ObjectMapper(),
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());

    service.initialize(id, "python");

    assertEquals(List.of("python"), service.supportedLanguagePacks(id));
  }

  @Test
  void treatsMissingLanguagePackDeclarationAsUnrestricted() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/empty"));
    Files.writeString(template.resolve("template.json"), "{\"name\":\"empty\"}");
    WorkspaceTaskService service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            new ObjectMapper(),
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());

    service.initialize(id, "empty");

    assertEquals(List.of(), service.supportedLanguagePacks(id));
  }

  @Test
  void rejectsMalformedOrDuplicateLanguagePackDeclarations() throws Exception {
    Path templates = Files.createDirectories(temp.resolve("templates"));
    WorkspaceTaskService service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            templates.toString(),
            new ObjectMapper(),
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));

    for (String languagePacks : List.of("\"python\"", "[\"python\",\"python\"]", "[\"unknown\"]")) {
      Path template = Files.createDirectories(templates.resolve("bad" + languagePacks.length()));
      Files.writeString(
          template.resolve("template.json"),
          "{\"name\":\"bad"
              + languagePacks.length()
              + "\",\"languagePacks\":"
              + languagePacks
              + "}");
      assertThrows(
          IllegalArgumentException.class,
          () ->
              service.initialize(
                  new WorkspaceId(UUID.randomUUID()), template.getFileName().toString()));
    }
  }

  @Test
  void rejectsTemplateDirectorySymlink() throws Exception {
    Path actual = Files.createDirectories(temp.resolve("actual"));
    Files.writeString(actual.resolve("template.json"), "{}");
    Path root = Files.createDirectories(temp.resolve("templates"));
    try {
      Files.createSymbolicLink(root.resolve("outside"), actual);
    } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
      org.junit.jupiter.api.Assumptions.abort("symbolic links are unavailable");
    }
    WorkspaceTaskService service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            root.toString(),
            new ObjectMapper(),
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.initialize(new WorkspaceId(UUID.randomUUID()), "outside"));
  }

  @Test
  void stopsOnlyTheWorkspaceScopedTaskRun() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/node"));
    Files.writeString(template.resolve("template.json"), "{\"name\":\"node\"}");
    ObjectMapper json = new ObjectMapper();
    WorkspaceTaskServiceImpl service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            json,
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    UUID runId = UUID.randomUUID();
    service.initialize(workspace, "node");
    Path metadata = temp.resolve("data/workspace-state").resolve(workspace.value() + ".json");
    ObjectNode state = (ObjectNode) json.readTree(Files.readString(metadata));
    state.put("taskRunId", runId.toString());
    state.put("taskId", "start");
    state.put("taskLabel", "Start server");
    state.put("taskStatus", "RUNNING");
    state.put("taskStartedAt", java.time.Instant.now().toString());
    Files.writeString(metadata, json.writeValueAsString(state));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.stopTask(new WorkspaceId(UUID.randomUUID()), runId));
    WorkspaceTaskService.TaskRun stopped = service.stopTask(workspace, runId);

    assertNotNull(stopped);
    assertEquals("STOPPED", stopped.status());
    assertEquals(workspace.value(), stopped.workspaceId());
  }

  @Test
  void reportsTaskInterruptedByServerRestartInLatestRun() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/node"));
    Files.writeString(template.resolve("template.json"), "{\"name\":\"node\"}");
    ObjectMapper json = new ObjectMapper();
    WorkspaceTaskServiceImpl service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            json,
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    UUID runId = UUID.randomUUID();
    service.initialize(workspace, "node");
    Path metadata = temp.resolve("data/workspace-state").resolve(workspace.value() + ".json");
    ObjectNode state = (ObjectNode) json.readTree(Files.readString(metadata));
    state.put("taskRunId", runId.toString());
    state.put("taskId", "serve");
    state.put("taskLabel", "Serve preview");
    state.put("taskStatus", "RUNNING");
    state.put("taskOutput", "Starting preview server");
    state.put("taskStartedAt", Instant.now().toString());
    Files.writeString(metadata, json.writeValueAsString(state));

    service.run(mock(ApplicationArguments.class));

    WorkspaceTaskService.TaskRun recovered = service.latestTaskRun(workspace);
    assertNotNull(recovered);
    assertEquals(runId, recovered.id());
    assertEquals("FAILED", recovered.status());
    assertTrue(recovered.output().contains("Starting preview server"));
    assertTrue(recovered.output().contains("Task was interrupted by a server restart."));
    assertNotNull(recovered.finishedAt());
    assertNull(service.latestTaskRun(new WorkspaceId(UUID.randomUUID())));
  }

  @Test
  void retrySetupLeavesReadySetupUntouched() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/node"));
    Files.writeString(
        template.resolve("template.json"),
        """
        {"name":"node","setupSteps":[{"id":"install","command":["not-used"]}]}
        """);
    ObjectMapper json = new ObjectMapper();
    WorkspaceTaskServiceImpl service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            json,
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    service.initialize(workspace, "node");
    Path metadata = temp.resolve("data/workspace-state").resolve(workspace.value() + ".json");
    ObjectNode state = (ObjectNode) json.readTree(Files.readString(metadata));
    state.put("setupStatus", "READY");
    state.put("setupOutput", "setup already completed");
    state.put("updatedAt", Instant.EPOCH.toString());
    Files.writeString(metadata, json.writeValueAsString(state));

    WorkspaceTaskService.SetupStatus result = service.retrySetup(workspace);

    assertEquals("READY", result.status());
    assertEquals("setup already completed", result.output());
    assertEquals(Instant.EPOCH, result.updatedAt());
  }

  @Test
  void setupAttemptExposesAStableCorrelationIdUntilItFails() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/node"));
    Files.writeString(
        template.resolve("template.json"),
        """
        {"name":"node","setupSteps":[{"id":"install","command":["missing-jahaziel-setup-command"]}]}
        """);
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    WorkspaceService workspaces = mock(WorkspaceService.class);
    when(workspaces.get(workspace)).thenReturn(activeWorkspace(workspace));
    WorkspaceTaskService service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            new ObjectMapper(),
            workspaces,
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    service.initialize(workspace, "node");

    WorkspaceTaskService.SetupStatus firstAttempt = service.retrySetup(workspace);
    assertNotNull(firstAttempt.correlationId());
    WorkspaceTaskService.SetupStatus firstFailure = awaitSetupStatus(service, workspace, "FAILED");
    assertEquals(firstAttempt.correlationId(), firstFailure.correlationId());
  }

  @Test
  void readsExistingSetupMetadataWithoutACorrelationField() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/empty"));
    Files.writeString(template.resolve("template.json"), "{\"name\":\"empty\"}");
    ObjectMapper json = new ObjectMapper();
    WorkspaceTaskService service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            json,
            mock(WorkspaceService.class),
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    service.initialize(workspace, "empty");
    Path metadata = temp.resolve("data/workspace-state").resolve(workspace.value() + ".json");
    ObjectNode legacyState = (ObjectNode) json.readTree(Files.readString(metadata));
    legacyState.remove("setupCorrelationId");
    Files.writeString(metadata, json.writeValueAsString(legacyState));

    WorkspaceTaskService.SetupStatus status = service.setupStatus(workspace);

    assertEquals("READY", status.status());
    assertNull(status.correlationId());
  }

  @Test
  void setupRetryWaitsUntilTheWorkspaceStartsAgain() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/node"));
    Files.writeString(
        template.resolve("template.json"),
        """
        {"name":"node","setupSteps":[{"id":"install","command":["not-used"]}]}
        """);
    ObjectMapper json = new ObjectMapper();
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    WorkspaceService workspaces = mock(WorkspaceService.class);
    Workspace stoppedWorkspace = newWorkspace(workspace);
    when(workspaces.get(workspace)).thenReturn(stoppedWorkspace);
    WorkspaceTaskServiceImpl service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            json,
            workspaces,
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    service.initialize(workspace, "node");
    Path metadata = temp.resolve("data/workspace-state").resolve(workspace.value() + ".json");
    ObjectNode state = (ObjectNode) json.readTree(Files.readString(metadata));
    state.put("setupStatus", "FAILED");
    state.put("updatedAt", Instant.EPOCH.toString());
    Files.writeString(metadata, json.writeValueAsString(state));
    service.closeByWorkspace(workspace);

    WorkspaceTaskService.SetupStatus blocked = service.retrySetup(workspace);

    assertEquals("FAILED", blocked.status());
    assertEquals(Instant.EPOCH, blocked.updatedAt());
    assertTrue(blocked.error().contains("workspace must be started"));

    service.allowByWorkspace(workspace);
    stoppedWorkspace.prepare();
    stoppedWorkspace.boot();
    stoppedWorkspace.markReady();
    stoppedWorkspace.start();
    WorkspaceTaskService.SetupStatus retried = service.retrySetup(workspace);
    assertNotEquals(Instant.EPOCH, retried.updatedAt());
    service.closeAll();
  }

  @Test
  void setupRetryAfterRestartRequiresThePersistedWorkspaceToBeStarted() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/node"));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    Path marker =
        temp.resolve("data/workspaces")
            .resolve(workspace.value().toString())
            .resolve("home/setup-must-not-run");
    Files.writeString(
        template.resolve("template.json"),
        """
        {"name":"node","setupSteps":[{"id":"install","command":["/bin/sh","-c","touch setup-must-not-run"]}]}
        """);
    ObjectMapper json = new ObjectMapper();
    WorkspaceService workspaces = mock(WorkspaceService.class);
    Workspace stoppedWorkspace = newWorkspace(workspace);
    when(workspaces.get(workspace)).thenReturn(stoppedWorkspace);
    WorkspaceTaskServiceImpl service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            json,
            workspaces,
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    service.initialize(workspace, "node");
    Path metadata = temp.resolve("data/workspace-state").resolve(workspace.value() + ".json");
    ObjectNode state = (ObjectNode) json.readTree(Files.readString(metadata));
    state.put("setupStatus", "FAILED");
    state.put("updatedAt", Instant.EPOCH.toString());
    Files.writeString(metadata, json.writeValueAsString(state));

    WorkspaceTaskService.SetupStatus blocked = service.retrySetup(workspace);

    assertEquals("FAILED", blocked.status());
    assertEquals(Instant.EPOCH, blocked.updatedAt());
    assertTrue(blocked.error().contains("workspace must be started"));
    assertFalse(Files.exists(marker));
    service.closeAll();
  }

  @Test
  void taskCannotStartAfterWorkspaceStopBegins() throws Exception {
    Path template = Files.createDirectories(temp.resolve("templates/node"));
    Files.writeString(
        template.resolve("template.json"),
        """
        {"name":"node","runTasks":[{"id":"start","label":"Start","command":["echo","must-not-run"]}]}
        """);
    ObjectMapper json = new ObjectMapper();
    WorkspaceService workspaces = mock(WorkspaceService.class);
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    Workspace active = activeWorkspace(workspaceId);
    when(workspaces.get(workspaceId)).thenReturn(active);
    WorkspaceTaskServiceImpl service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            json,
            workspaces,
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    service.initialize(workspaceId, "node");
    service.closeByWorkspace(workspaceId);

    IllegalStateException stopped =
        assertThrows(IllegalStateException.class, () -> service.startTask(workspaceId, "start"));

    assertEquals("workspace is stopping", stopped.getMessage());
  }

  @Test
  void stoppingWorkspaceTerminatesSetupProcessTreeAndKeepsProgress() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name", "").toLowerCase().contains("linux"),
        "workspace setup processes require Linux");
    Path template = Files.createDirectories(temp.resolve("templates/node"));
    Path shellPidFile = temp.resolve("setup-shell.pid");
    Path childPidFile = temp.resolve("setup-child.pid");
    Path completedFile = temp.resolve("setup-completed");
    String script =
        "printf 'preparing\\n'; echo $$ > "
            + shellPidFile
            + "; sleep 60 & child=$!; echo $child > "
            + childPidFile
            + "; wait; touch "
            + completedFile;
    ObjectMapper json = new ObjectMapper();
    ObjectNode manifest = json.createObjectNode();
    manifest.put("name", "node");
    ObjectNode step = manifest.putArray("setupSteps").addObject();
    step.put("id", "hold");
    step.putArray("command").add("/bin/sh").add("-c").add(script);
    Files.writeString(template.resolve("template.json"), json.writeValueAsString(manifest));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    WorkspaceService workspaces = mock(WorkspaceService.class);
    when(workspaces.get(workspace)).thenReturn(activeWorkspace(workspace));
    WorkspaceTaskServiceImpl service =
        new WorkspaceTaskServiceImpl(
            temp.resolve("data").toString(),
            temp.resolve("templates").toString(),
            json,
            workspaces,
            new WorkspaceDirectoryServiceImpl(temp.resolve("data").toString()));
    service.initialize(workspace, "node");

    try {
      service.retrySetup(workspace);
      awaitFile(childPidFile);
      awaitSetupOutput(service, workspace, "preparing");
      long shellPid = Long.parseLong(Files.readString(shellPidFile).trim());
      long childPid = Long.parseLong(Files.readString(childPidFile).trim());

      service.closeByWorkspace(workspace);

      WorkspaceTaskService.SetupStatus stopped = awaitSetupStatus(service, workspace, "FAILED");
      assertTrue(stopped.error().contains("workspace stopped"));
      assertTrue(stopped.output().contains("preparing"));
      awaitProcessExit(shellPid);
      awaitProcessExit(childPid);
      assertFalse(Files.exists(completedFile));
    } finally {
      service.closeAll();
    }
  }

  private static void awaitFile(Path path) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline && !Files.isRegularFile(path)) Thread.sleep(10);
    assertTrue(Files.isRegularFile(path), "setup child did not start");
  }

  private static WorkspaceTaskService.SetupStatus awaitSetupStatus(
      WorkspaceTaskService service, WorkspaceId workspace, String expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    WorkspaceTaskService.SetupStatus status = service.setupStatus(workspace);
    while (System.nanoTime() < deadline && !expected.equals(status.status())) {
      Thread.sleep(10);
      status = service.setupStatus(workspace);
    }
    assertEquals(expected, status.status());
    return status;
  }

  private static void awaitSetupOutput(
      WorkspaceTaskService service, WorkspaceId workspace, String expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline
        && !service.setupStatus(workspace).output().contains(expected)) {
      Thread.sleep(10);
    }
    assertTrue(service.setupStatus(workspace).output().contains(expected));
  }

  private static void awaitProcessExit(long pid) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline
        && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
      Thread.sleep(10);
    }
    assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
  }

  private static Workspace activeWorkspace(WorkspaceId id) {
    Workspace workspace = newWorkspace(id);
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    return workspace;
  }

  private static Workspace newWorkspace(WorkspaceId id) {
    return new Workspace(
        id,
        new UserId(UUID.randomUUID()),
        "project",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()), id, new VirtualMachineConfiguration(2, 2048)));
  }
}
