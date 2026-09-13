/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.runtime.domain.VirtualMachine;
import dev.jahaziel.runtime.domain.VirtualMachineConfiguration;
import dev.jahaziel.runtime.domain.VirtualMachineId;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.spi.UserIdentity;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Endpoints of {@link WorkspaceController} with stubbed services. Standalone MockMvc: no Spring
 * Boot context, so no database is needed.
 */
class WorkspaceControllerTest {
  private MockMvc mvc;

  private WorkspaceService workspaces;

  private RuntimeService runtime;

  private AuthorizationService auth;

  private UserService users;

  private final UserId userId = new UserId(UUID.randomUUID());
  private final Principal principal = () -> userId.value().toString();

  /** Builds standalone MockMvc around the controller and the exception handler. */
  @BeforeEach
  void setUp() {
    workspaces = mock(WorkspaceService.class);
    runtime = mock(RuntimeService.class);
    auth = mock(AuthorizationService.class);
    users = mock(UserService.class);
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc =
        MockMvcBuilders.standaloneSetup(
                new WorkspaceController(workspaces, runtime, auth, users, "code.test", "node-a"))
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @Test
  void meReturnsIdentity() throws Exception {
    when(users.get(userId))
        .thenReturn(new UserIdentity("sub", "a@b.c", "A", List.of("developer"), List.of()));
    when(users.isMfaEnabled(userId)).thenReturn(true);
    mvc.perform(get("/api/v1/me").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("a@b.c"))
        .andExpect(jsonPath("$.mfaEnabled").value(true));
  }

  @Test
  void listMineReturnsViews() throws Exception {
    when(workspaces.describeByOwner(userId)).thenReturn(List.of(view()));
    mvc.perform(get("/api/v1/me/workspaces").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("club"))
        .andExpect(jsonPath("$[0].urls.ide").value(startsWith("wss://")));
  }

  @Test
  void createReturnsDto() throws Exception {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    when(workspaces.create(eq(userId), eq("club"), eq(2), eq(2048L), any()))
        .thenReturn(workspace(id));
    when(workspaces.describe(id)).thenReturn(view(id));
    mvc.perform(
            post("/api/v1/workspaces")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"club\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.state").value("requested"));
  }

  @Test
  void createRejectsBadInput() throws Exception {
    mvc.perform(
            post("/api/v1/workspaces")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(
            post("/api/v1/workspaces")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"club\",\"memoryMib\":1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingWorkspaceIsNotFound() throws Exception {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    when(workspaces.describe(id)).thenThrow(new WorkspaceNotFoundException(id));
    mvc.perform(get("/api/v1/workspaces/" + id.value()).principal(principal))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void forbiddenWorkspaceIsForbidden() throws Exception {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    when(auth.isAllowed(eq(userId), eq("read"), eq(id))).thenReturn(false);
    mvc.perform(get("/api/v1/workspaces/" + id.value()).principal(principal))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void deleteNeedsConfirmation() throws Exception {
    UUID id = UUID.randomUUID();
    mvc.perform(delete("/api/v1/workspaces/" + id).principal(principal))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/v1/workspaces/" + id).principal(principal).param("confirm", "true"))
        .andExpect(status().isNoContent());
    verify(workspaces).delete(new WorkspaceId(id));
  }

  private WorkspaceView view() {
    return view(new WorkspaceId(UUID.randomUUID()));
  }

  private WorkspaceView view(WorkspaceId id) {
    return new WorkspaceView(
        workspace(id), Instant.now(), Instant.now(), new StorageUsage(0, 0, 0, 0));
  }

  private Workspace workspace(WorkspaceId id) {
    return new Workspace(
        id,
        userId,
        "club",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()), id, new VirtualMachineConfiguration(2, 2048)));
  }
}
