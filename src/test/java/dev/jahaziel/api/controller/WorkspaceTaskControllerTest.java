/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkspaceTaskControllerTest {
  private final UserId userId = new UserId(UUID.randomUUID());
  private final WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
  private final Principal principal = () -> userId.value().toString();
  private WorkspaceTaskService tasks;
  private AuthorizationService auth;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    tasks = mock(WorkspaceTaskService.class);
    auth = mock(AuthorizationService.class);
    when(auth.isAllowed(userId, "read", workspaceId)).thenReturn(true);
    mvc =
        MockMvcBuilders.standaloneSetup(new WorkspaceTaskController(tasks, auth))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void latestTaskRunReturnsMostRecentRun() throws Exception {
    UUID runId = UUID.randomUUID();
    when(tasks.latestTaskRun(workspaceId))
        .thenReturn(
            new WorkspaceTaskService.TaskRun(
                runId,
                workspaceId.value(),
                "serve",
                "Serve preview",
                "FAILED",
                "preview started\nTask was interrupted by a server restart.",
                null,
                Instant.parse("2026-11-07T10:00:00Z"),
                Instant.parse("2026-11-07T10:01:00Z")));

    mvc.perform(
            get("/api/v1/workspaces/" + workspaceId.value() + "/task-runs/latest")
                .principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(runId.toString()))
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.output").value(org.hamcrest.Matchers.containsString("interrupted")));

    verify(auth).isAllowed(userId, "read", workspaceId);
    verify(tasks).latestTaskRun(workspaceId);
  }

  @Test
  void latestTaskRunReturnsNoContentWhenWorkspaceHasNoRun() throws Exception {
    when(tasks.latestTaskRun(workspaceId)).thenReturn(null);

    mvc.perform(
            get("/api/v1/workspaces/" + workspaceId.value() + "/task-runs/latest")
                .principal(principal))
        .andExpect(status().isNoContent());

    verify(tasks).latestTaskRun(workspaceId);
  }

  @Test
  void latestTaskRunRequiresWorkspaceReadAuthorization() throws Exception {
    when(auth.isAllowed(eq(userId), eq("read"), eq(workspaceId))).thenReturn(false);

    mvc.perform(
            get("/api/v1/workspaces/" + workspaceId.value() + "/task-runs/latest")
                .principal(principal))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(tasks);
  }
}
