/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.persistence.WorkspacePortEntity;
import dev.jahaziel.networking.persistence.WorkspacePortRepository;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.spi.UserIdentity;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link PortServiceImpl} with stubbed collaborators. */
class PortServiceImplTest {
  private WorkspacePortRepository ports;
  private UserService users;
  private WorkspaceService workspaces;
  private PortServiceImpl service;
  private UserId userId;
  private WorkspaceId workspaceId;

  @BeforeEach
  void setUp() {
    ports = mock(WorkspacePortRepository.class);
    users = mock(UserService.class);
    workspaces = mock(WorkspaceService.class);
    service = new PortServiceImpl(ports, users, workspaces);
    userId = new UserId(UUID.randomUUID());
    workspaceId = new WorkspaceId(UUID.randomUUID());
  }

  @Test
  void registersBinding() {
    when(users.get(userId)).thenReturn(identity());
    when(workspaces.get(workspaceId)).thenReturn(workspace());
    PortBinding binding = service.registerPort(userId, 8080, workspaceId, "http");
    assertEquals(8080, binding.port());
    assertEquals("http", binding.protocol());
    verify(ports).save(any());
  }

  @Test
  void rejectsReservedDuplicateAndForeign() {
    when(users.get(userId)).thenReturn(identity());
    when(workspaces.get(workspaceId)).thenReturn(workspace());
    assertThrows(
        IllegalArgumentException.class, () -> service.registerPort(userId, 22, workspaceId, "tcp"));
    WorkspacePortEntity.Id key = new WorkspacePortEntity.Id();
    key.setWorkspaceId(workspaceId.value());
    key.setPort(8080);
    when(ports.existsById(key)).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.registerPort(userId, 8080, workspaceId, "http"));
    UserId stranger = new UserId(UUID.randomUUID());
    when(users.get(stranger)).thenReturn(identity());
    assertThrows(
        IllegalArgumentException.class,
        () -> service.registerPort(stranger, 8080, workspaceId, "http"));
  }

  @Test
  void unregistersKnownPort() {
    WorkspacePortEntity row = mock(WorkspacePortEntity.class);
    WorkspacePortEntity.Id key = mock(WorkspacePortEntity.Id.class);
    when(row.getId()).thenReturn(key);
    when(key.getWorkspaceId()).thenReturn(workspaceId.value());
    when(key.getPort()).thenReturn(8080);
    when(workspaces.describeByOwner(userId)).thenReturn(List.of(view()));
    when(ports.findByIdWorkspaceId(workspaceId.value())).thenReturn(List.of(row));
    service.unregisterPort(userId, 8080);
    verify(ports).delete(row);
    assertThrows(IllegalArgumentException.class, () -> service.unregisterPort(userId, 9090));
  }

  @Test
  void lookupFindsWorkspace() {
    WorkspacePortEntity row = mock(WorkspacePortEntity.class);
    WorkspacePortEntity.Id key = mock(WorkspacePortEntity.Id.class);
    when(row.getId()).thenReturn(key);
    when(key.getWorkspaceId()).thenReturn(workspaceId.value());
    when(key.getPort()).thenReturn(8080);
    when(workspaces.describeByOwner(userId)).thenReturn(List.of(view()));
    when(ports.findByIdWorkspaceId(workspaceId.value())).thenReturn(List.of(row));
    assertEquals(Optional.of(workspaceId), service.lookup(userId.value(), 8080));
    assertTrue(service.lookup(userId.value(), 9090).isEmpty());
  }

  private UserIdentity identity() {
    return new UserIdentity("sub", "a@b.c", "A", List.of(), List.of());
  }

  private Workspace workspace() {
    return new Workspace(
        workspaceId,
        userId,
        "club",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(2, 2048)));
  }

  private WorkspaceView view() {
    Instant at = Instant.now();
    return new WorkspaceView(workspace(), at, at, new StorageUsage(0, 0, 0, 0));
  }
}
