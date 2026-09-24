/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.UserIdentity;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.domain.WorkspaceState;
import dev.jahaziel.workspace.persistence.VirtualMachineEntity;
import dev.jahaziel.workspace.persistence.VirtualMachineRepository;
import dev.jahaziel.workspace.persistence.WorkspaceAclRepository;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceJobsConfigCoverageGapTest {
  @Test
  void cleanupCtorGuards() {
    var workspaces = mock(dev.jahaziel.workspace.api.WorkspaceService.class);
    assertThrows(NullPointerException.class, () -> new CleanupJob(null, "/r"));
    assertThrows(IllegalArgumentException.class, () -> new CleanupJob(workspaces, null));
    assertThrows(IllegalArgumentException.class, () -> new CleanupJob(workspaces, "  "));
  }

  @Test
  void cleanupNoJailReturns(@TempDir Path root) {
    var workspaces = mock(dev.jahaziel.workspace.api.WorkspaceService.class);
    when(workspaces.describeAll()).thenReturn(List.of());
    new CleanupJob(workspaces, root.toString()).run(JobContext.now());
  }

  @Test
  void cleanupKeepsKnownAndDeletesOrphan(@TempDir Path root) throws Exception {
    UUID knownId = UUID.randomUUID();
    UUID orphanId = UUID.randomUUID();
    Path jail = root.resolve("jail");
    Files.createDirectories(jail.resolve(knownId.toString()));
    Files.createDirectories(jail.resolve(orphanId.toString()));
    Files.writeString(jail.resolve(orphanId.toString()).resolve("x.txt"), "x");
    Files.writeString(jail.resolve("not-a-uuid"), "x");
    var workspaces = mock(dev.jahaziel.workspace.api.WorkspaceService.class);
    Workspace workspace = workspace(knownId);
    var view = mock(WorkspaceView.class);
    when(view.workspace()).thenReturn(workspace);
    when(workspaces.describeAll()).thenReturn(List.of(view));
    new CleanupJob(workspaces, root.toString()).run(JobContext.now());
    assertTrue(Files.isDirectory(jail.resolve(knownId.toString())));
    assertTrue(Files.notExists(jail.resolve(orphanId.toString())));
    assertTrue(Files.exists(jail.resolve("not-a-uuid")));
    CleanupJob job = new CleanupJob(workspaces, root.toString());
    assertEquals(Duration.ofHours(24), job.period());
    assertEquals(JobScope.NODE, job.scope());
  }

  @Test
  void cleanupListFailureThrows(@TempDir Path root) throws Exception {
    Path jail = root.resolve("jail");
    Files.createDirectories(jail);
    var workspaces = mock(dev.jahaziel.workspace.api.WorkspaceService.class);
    when(workspaces.describeAll()).thenReturn(List.of());
    try (var files =
        org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.list(any(Path.class))).thenThrow(new java.io.IOException("boom"));
      assertThrows(
          java.io.UncheckedIOException.class,
          () -> new CleanupJob(workspaces, root.toString()).run(JobContext.now()));
    }
  }

  @Test
  void backendConfigSelects(@TempDir Path root) {
    WorkspaceBackendConfig config = new WorkspaceBackendConfig();
    NodeBackend fire = config.nodeBackend("firecracker", root.toString());
    assertInstanceOf(FirecrackerBackend.class, fire);
    NodeBackend upper = config.nodeBackend("  FIRECRACKER  ", root.toString());
    assertInstanceOf(FirecrackerBackend.class, upper);
    assertInstanceOf(LocalBackend.class, config.nodeBackend("local", root.toString()));
    assertInstanceOf(LocalBackend.class, config.nodeBackend("other", root.toString()));
  }

  @Test
  void pathsGuards() {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    UserId owner = new UserId(UUID.randomUUID());
    assertTrue(WorkspacePaths.jailPath("/r", id).equals("/r/jail/" + id.value()));
    assertTrue(WorkspacePaths.homePath("/r", owner).endsWith("/home"));
    assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.jailPath(null, id));
    assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.jailPath("  ", id));
    assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.homePath("", owner));
  }

  @Test
  void serviceCtorGuards() {
    var workspaces = mock(WorkspaceRepository.class);
    var acl = mock(WorkspaceAclRepository.class);
    var machines = mock(VirtualMachineRepository.class);
    var users = mock(dev.jahaziel.identity.api.UserService.class);
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkspaceServiceImpl(workspaces, acl, machines, users, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkspaceServiceImpl(workspaces, acl, machines, users, "  "));
  }

  @Test
  void createRollsBackWhenMachineFails() {
    var workspaces = mock(WorkspaceRepository.class);
    var acl = mock(WorkspaceAclRepository.class);
    var machines = mock(VirtualMachineRepository.class);
    var users = mock(dev.jahaziel.identity.api.UserService.class);
    var service = new WorkspaceServiceImpl(workspaces, acl, machines, users, "/r");
    UserId owner = new UserId(UUID.randomUUID());
    when(users.get(owner)).thenReturn(new UserIdentity("s", "a@b.c", "A", List.of(), List.of()));
    when(workspaces.findByOwnerUserId(owner.value())).thenReturn(List.of());
    when(machines.save(any())).thenThrow(new RuntimeException("boom"));
    assertThrows(
        RuntimeException.class, () -> service.create(owner, "site", 1, 512, new NodeId("n")));
    verify(workspaces).deleteById(any(UUID.class));
  }

  @Test
  void getWithoutMachineRowThrows() {
    var workspaces = mock(WorkspaceRepository.class);
    var acl = mock(WorkspaceAclRepository.class);
    var machines = mock(VirtualMachineRepository.class);
    var users = mock(dev.jahaziel.identity.api.UserService.class);
    var service = new WorkspaceServiceImpl(workspaces, acl, machines, users, "/r");
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    WorkspaceEntity row = new WorkspaceEntity();
    row.setWorkspaceId(id.value());
    row.setOwnerUserId(UUID.randomUUID());
    row.setName("site");
    row.setState("requested");
    row.setStateChangedAt(Instant.now());
    row.setLogicalQuotaBytes(1L);
    row.setInodeQuota(1L);
    row.setCreatedAt(Instant.now());
    when(workspaces.findById(id.value())).thenReturn(Optional.of(row));
    when(acl.findByIdWorkspaceId(id.value())).thenReturn(List.of());
    when(machines.findByWorkspaceId(id.value())).thenReturn(Optional.empty());
    assertThrows(IllegalStateException.class, () -> service.get(id));
  }

  @Test
  void createRejectsNullAndLongNames() {
    var workspaces = mock(WorkspaceRepository.class);
    var acl = mock(WorkspaceAclRepository.class);
    var machines = mock(VirtualMachineRepository.class);
    var users = mock(dev.jahaziel.identity.api.UserService.class);
    var service = new WorkspaceServiceImpl(workspaces, acl, machines, users, "/r");
    UserId owner = new UserId(UUID.randomUUID());
    when(users.get(owner)).thenReturn(new UserIdentity("s", "a@b.c", "A", List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> service.create(owner, null, 1, 512, new NodeId("n")));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(owner, "x".repeat(65), 1, 512, new NodeId("n")));
  }

  @Test
  void mapperIdleAndUnknown() {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    UserId owner = new UserId(UUID.randomUUID());
    Instant at = Instant.now();
    WorkspaceEntity row = mock(WorkspaceEntity.class);
    when(row.getWorkspaceId()).thenReturn(workspaceId.value());
    when(row.getOwnerUserId()).thenReturn(owner.value());
    when(row.getName()).thenReturn("club");
    when(row.getState()).thenReturn("idle");
    when(row.getStateChangedAt()).thenReturn(at);
    when(row.getLogicalQuotaBytes()).thenReturn(1L);
    when(row.getInodeQuota()).thenReturn(1L);
    VirtualMachineEntity machine = mock(VirtualMachineEntity.class);
    when(machine.getVmId()).thenReturn(UUID.randomUUID());
    when(machine.getVcpuCount()).thenReturn(1);
    when(machine.getMemoryMib()).thenReturn(512L);
    Workspace workspace = WorkspaceMapper.toDomain(row, List.of(), machine, List.of());
    assertInstanceOf(WorkspaceState.Idle.class, workspace.state());
    assertThrows(IllegalArgumentException.class, () -> WorkspaceMapper.stateToString(null));
  }

  private Workspace workspace(UUID id) {
    WorkspaceId workspaceId = new WorkspaceId(id);
    return new Workspace(
        workspaceId,
        new UserId(UUID.randomUUID()),
        "club",
        new WorkspaceQuota(1L, 1L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(1, 512)));
  }
}
