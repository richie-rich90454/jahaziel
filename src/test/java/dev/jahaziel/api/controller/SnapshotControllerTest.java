/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** Endpoints of {@link SnapshotController} with stubbed services. Standalone MockMvc. */
class SnapshotControllerTest {
  private MockMvc mvc;

  private SnapshotService snapshots;
  private WorkspaceService workspaces;
  private AuthorizationService auth;
  private WorkspaceDtoFactory dtos;

  private final WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
  private final Principal principal = () -> UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    snapshots = mock(SnapshotService.class);
    workspaces = mock(WorkspaceService.class);
    auth = mock(AuthorizationService.class);
    PortService ports = mock(PortService.class);
    JwtService tokens = mock(JwtService.class);
    UserService users = mock(UserService.class);
    when(ports.bindingsByWorkspace(any())).thenReturn(List.of());
    dtos = new WorkspaceDtoFactory(ports, users, tokens, "code.test", 900);
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc =
        MockMvcBuilders.standaloneSetup(new SnapshotController(snapshots, workspaces, auth, dtos))
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @Test
  void listsSnapshots() throws Exception {
    when(snapshots.list(workspaceId)).thenReturn(List.of(metadata()));
    mvc.perform(
            get("/api/v1/workspaces/" + workspaceId.value() + "/snapshots").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].tag").value("daily"))
        .andExpect(jsonPath("$[0].kind").value("manual"));
  }

  @Test
  void createsSnapshot() throws Exception {
    when(snapshots.create(eq(workspaceId), eq("daily"), eq(SnapshotKind.MANUAL), any()))
        .thenReturn(metadata());
    mvc.perform(
            post("/api/v1/workspaces/" + workspaceId.value() + "/snapshots")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\":\"daily\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tag").value("daily"));
  }

  @Test
  void rejectsBadTags() throws Exception {
    mvc.perform(
            post("/api/v1/workspaces/" + workspaceId.value() + "/snapshots")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\":\"has space\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void restoresSnapshot() throws Exception {
    SnapshotId snapshotId = new SnapshotId(UUID.randomUUID());
    Instant at = Instant.now();
    when(workspaces.describe(workspaceId))
        .thenReturn(new WorkspaceView(workspace(), at, at, new StorageUsage(0, 0, 0, 0)));
    mvc.perform(
            post("/api/v1/workspaces/"
                    + workspaceId.value()
                    + "/snapshots/"
                    + snapshotId.value()
                    + "/restore")
                .principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaceId").value(workspaceId.value().toString()));
    verify(snapshots).restore(workspaceId, snapshotId);
  }

  private SnapshotMetadata metadata() {
    return new SnapshotMetadata(
        new SnapshotId(UUID.randomUUID()),
        workspaceId,
        "daily",
        SnapshotKind.MANUAL,
        Instant.parse("2026-01-02T03:04:05Z"),
        "user:x",
        Path.of("/snaps/daily"),
        0,
        0,
        Optional.empty(),
        "");
  }

  private Workspace workspace() {
    return new Workspace(
        workspaceId,
        new UserId(UUID.randomUUID()),
        "club",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(2, 2048)));
  }
}
