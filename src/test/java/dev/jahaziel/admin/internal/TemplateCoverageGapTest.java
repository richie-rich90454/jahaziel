/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

class TemplateCoverageGapTest {
  @TempDir private Path tmp;

  private Principal principal(UUID id) {
    return () -> id.toString();
  }

  private TemplateController controller(Path dir) {
    return controller(mock(WorkspaceService.class), mock(WorkspaceDtoFactory.class), dir);
  }

  private TemplateController controller(
      WorkspaceService workspaces, WorkspaceDtoFactory dtos, Path dir) {
    WorkspaceFiles files = mock(WorkspaceFiles.class);
    when(files.forWorkspace(any())).thenReturn(mock(WorkspaceFiles.Operations.class));
    return new TemplateController(
        workspaces,
        files,
        mock(WorkspaceTaskService.class),
        mock(PortService.class),
        dtos,
        new ObjectMapper(),
        dir.toString(),
        "node-a");
  }

  @Test
  void listEmptyWhenMissing() {
    assertTrue(controller(tmp.resolve("missing")).list().isEmpty());
  }

  @Test
  void listSortedSkipsNonTemplates() throws Exception {
    Path dir = tmp.resolve("templates");
    Files.createDirectories(dir.resolve("b"));
    Files.createDirectories(dir.resolve("a"));
    Files.createDirectories(dir.resolve("empty"));
    Files.writeString(dir.resolve("a").resolve("template.json"), "{}");
    Files.writeString(dir.resolve("b").resolve("template.json"), "{}");
    Files.writeString(dir.resolve("f.txt"), "hi");
    assertEquals(java.util.List.of("a", "b"), controller(dir).list());
  }

  @Test
  void listFailureWraps() {
    try (var files = Mockito.mockStatic(Files.class)) {
      files.when(() -> Files.isDirectory(any(Path.class))).thenReturn(true);
      files.when(() -> Files.list(any(Path.class))).thenThrow(new IOException("boom"));
      assertThrows(UncheckedIOException.class, () -> controller(tmp).list());
    }
  }

  @Test
  void getServesBytes() throws Exception {
    Path dir = tmp.resolve("t");
    Files.createDirectories(dir.resolve("foo"));
    Files.writeString(dir.resolve("foo").resolve("template.json"), "{\"v\":1}");
    var response = controller(dir).get("foo");
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
    assertEquals("{\"v\":1}", new String(response.getBody()));
  }

  @Test
  void getMissingIs404() throws Exception {
    Path dir = tmp.resolve("t404");
    Files.createDirectories(dir);
    assertEquals(HttpStatus.NOT_FOUND, controller(dir).get("nope").getStatusCode());
  }

  @Test
  void getRejectsTraversal() throws Exception {
    Path dir = tmp.resolve("trav");
    Files.createDirectories(dir);
    assertThrows(IllegalArgumentException.class, () -> controller(dir).get(".."));
    assertThrows(IllegalArgumentException.class, () -> controller(dir).get("../x"));
    assertThrows(IllegalArgumentException.class, () -> controller(dir).get("/abs"));
  }

  @Test
  void getReadFailureWraps() {
    try (var files = Mockito.mockStatic(Files.class)) {
      files.when(() -> Files.isRegularFile(any(Path.class))).thenReturn(true);
      files.when(() -> Files.readAllBytes(any(Path.class))).thenThrow(new IOException("boom"));
      assertThrows(UncheckedIOException.class, () -> controller(tmp).get("foo"));
    }
  }

  @Test
  void applyValidatesRequest() throws Exception {
    Path dir = tmp.resolve("apply-bad");
    Files.createDirectories(dir);
    var controller = controller(dir);
    UUID user = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class, () -> controller.apply("t", null, principal(user)));
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.apply("t", new TemplateController.ApplyRequest("  "), principal(user)));
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.apply("t", new TemplateController.ApplyRequest(null), principal(user)));
  }

  @Test
  void applyRejectsStarterFilesAboveTheTotalByteLimitBeforeWorkspaceCreation() throws Exception {
    Path dir = tmp.resolve("apply-large-template");
    Files.createDirectories(dir.resolve("large"));
    java.util.Map<String, String> starterFiles = new java.util.LinkedHashMap<>();
    for (int index = 0; index < 8; index++) {
      starterFiles.put("file-" + index + ".txt", "x".repeat(256 * 1024));
    }
    starterFiles.put("overflow.txt", "x");
    Files.writeString(
        dir.resolve("large").resolve("template.json"),
        new ObjectMapper().writeValueAsString(java.util.Map.of("starterFiles", starterFiles)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            controller(dir)
                .apply(
                    "large",
                    new TemplateController.ApplyRequest("Large workspace"),
                    principal(UUID.randomUUID())));
  }

  @Test
  void applyRejectsMoreThanTheWorkspacePortLimitBeforeWorkspaceCreation() throws Exception {
    Path dir = tmp.resolve("apply-too-many-ports");
    Files.createDirectories(dir.resolve("many"));
    Files.writeString(
        dir.resolve("many").resolve("template.json"),
        new ObjectMapper()
            .writeValueAsString(
                java.util.Map.of(
                    "ports",
                    java.util.stream.IntStream.rangeClosed(
                            1, PortService.MAX_PORTS_PER_WORKSPACE + 1)
                        .boxed()
                        .toList())));
    var workspaces = mock(WorkspaceService.class);
    var controller = controller(workspaces, mock(WorkspaceDtoFactory.class), dir);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            controller.apply(
                "many",
                new TemplateController.ApplyRequest("Too many ports"),
                principal(UUID.randomUUID())));
    Mockito.verify(workspaces, Mockito.never())
        .create(any(UserId.class), anyString(), anyInt(), anyLong(), any(NodeId.class));
  }

  @Test
  void applyCreatesWorkspace() throws Exception {
    Path dir = tmp.resolve("apply-ok");
    Files.createDirectories(dir.resolve("full"));
    Files.writeString(
        dir.resolve("full").resolve("template.json"), "{\"vcpuCount\":4,\"memoryMib\":1024}");
    var workspaces = mock(WorkspaceService.class);
    var dtos = mock(WorkspaceDtoFactory.class);
    var controller = controller(workspaces, dtos, dir);
    UserId owner = new UserId(UUID.randomUUID());
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    Workspace workspace =
        new Workspace(
            id,
            owner,
            "ws",
            new WorkspaceQuota(21474836480L, 1000000L),
            new VirtualMachine(
                new VirtualMachineId(UUID.randomUUID()),
                id,
                new VirtualMachineConfiguration(2, 2048)));
    var view =
        new WorkspaceView(workspace, Instant.now(), Instant.now(), new StorageUsage(0, 0, 0, 0));
    WorkspaceDto dto = mock(WorkspaceDto.class);
    when(workspaces.create(any(), any(), any(int.class), any(long.class), any()))
        .thenReturn(workspace);
    when(workspaces.describe(id)).thenReturn(view);
    when(dtos.forUser(owner, view)).thenReturn(dto);
    assertSame(
        dto,
        controller.apply(
            "full", new TemplateController.ApplyRequest("ws"), principal(owner.value())));
    verify(workspaces).create(owner, "ws", 4, 1024L, new NodeId("node-a"));
  }

  @Test
  void applyDefaultsAndRejectsBadPrincipal() throws Exception {
    Path dir = tmp.resolve("apply-defaults");
    Files.createDirectories(dir.resolve("mini"));
    Files.writeString(dir.resolve("mini").resolve("template.json"), "{}");
    var workspaces = mock(WorkspaceService.class);
    var dtos = mock(WorkspaceDtoFactory.class);
    var controller = controller(workspaces, dtos, dir);
    UserId owner = new UserId(UUID.randomUUID());
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    Workspace workspace =
        new Workspace(
            id,
            owner,
            "ws",
            new WorkspaceQuota(21474836480L, 1000000L),
            new VirtualMachine(
                new VirtualMachineId(UUID.randomUUID()),
                id,
                new VirtualMachineConfiguration(2, 2048)));
    var view =
        new WorkspaceView(workspace, Instant.now(), Instant.now(), new StorageUsage(0, 0, 0, 0));
    when(workspaces.create(any(), any(), any(int.class), any(long.class), any()))
        .thenReturn(workspace);
    when(workspaces.describe(id)).thenReturn(view);
    when(dtos.forUser(any(), any())).thenReturn(mock(WorkspaceDto.class));
    controller.apply("mini", new TemplateController.ApplyRequest("ws"), principal(owner.value()));
    verify(workspaces).create(owner, "ws", 2, 2048L, new NodeId("node-a"));
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.apply("mini", new TemplateController.ApplyRequest("ws"), () -> "nope"));
  }

  @Test
  void applyMissingTemplateIsNotFound() throws Exception {
    Path dir = tmp.resolve("apply-missing");
    Files.createDirectories(dir);
    var controller = controller(dir);
    org.springframework.web.server.ResponseStatusException missing =
        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () ->
                controller.apply(
                    "nope",
                    new TemplateController.ApplyRequest("ws"),
                    principal(UUID.randomUUID())));
    assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
  }
}
