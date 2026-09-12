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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jahaziel.administration.api.AdminService;
import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.AppUser;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.monitoring.api.MetricsService;
import dev.jahaziel.node.api.NodeService;
import dev.jahaziel.node.domain.Node;
import dev.jahaziel.node.domain.NodeCapacity;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.policy.api.PolicyDoc;
import dev.jahaziel.policy.api.PolicyService;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Endpoints of {@link AdminController} with stubbed services. Standalone MockMvc. */
class AdminControllerTest {
  private MockMvc mvc;

  private AdminService admin;
  private AuthorizationService auth;
  private UserService users;
  private WorkspaceService workspaces;
  private NodeService nodes;
  private AuditService audit;
  private PolicyService policies;
  private MetricsService metrics;

  private final UserId adminId = new UserId(UUID.randomUUID());
  private final Principal principal = () -> adminId.value().toString();

  @BeforeEach
  void setUp() {
    admin = mock(AdminService.class);
    auth = mock(AuthorizationService.class);
    users = mock(UserService.class);
    workspaces = mock(WorkspaceService.class);
    nodes = mock(NodeService.class);
    audit = mock(AuditService.class);
    policies = mock(PolicyService.class);
    metrics = mock(MetricsService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new AdminController(
                    admin, auth, users, workspaces, nodes, audit, policies, metrics, "code.test"))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    when(auth.isAdmin(adminId)).thenReturn(true);
    when(users.isMfaEnabled(adminId)).thenReturn(true);
  }

  @Test
  void suspendsAndUnsuspends() throws Exception {
    UUID id = UUID.randomUUID();
    mvc.perform(
            post("/admin/v1/users/" + id + "/suspend")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"abuse\"}"))
        .andExpect(status().isNoContent());
    verify(admin).suspendUser(new UserId(id), "abuse", adminId);
    mvc.perform(
            post("/admin/v1/users/" + id + "/unsuspend")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"appeal\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void nonAdminIsForbidden() throws Exception {
    when(auth.isAdmin(adminId)).thenReturn(false);
    mvc.perform(get("/admin/v1/users").principal(principal))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void adminWithoutMfaIsForbidden() throws Exception {
    when(users.isMfaEnabled(adminId)).thenReturn(false);
    mvc.perform(get("/admin/v1/users").principal(principal)).andExpect(status().isForbidden());
  }

  @Test
  void listsUsers() throws Exception {
    AppUser user =
        new AppUser(adminId, "sub", "a@b.c", "A", List.of("ROLE_ADMIN"), List.of(), true);
    when(admin.listUsers()).thenReturn(List.of(user));
    mvc.perform(get("/admin/v1/users").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("a@b.c"));
  }

  @Test
  void putsAndDeletesPolicy() throws Exception {
    UUID id = UUID.randomUUID();
    PolicyDoc doc =
        new PolicyDoc(id, "global", null, "{}", Instant.parse("2026-01-02T03:04:05Z"), "admin");
    when(policies.create(eq("global"), eq(null), eq("{}"), any())).thenReturn(doc);
    mvc.perform(
            put("/admin/v1/policy")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"global\",\"scopeId\":null,\"policyJson\":\"{}\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scope").value("global"));
    mvc.perform(delete("/admin/v1/policy/" + id).principal(principal))
        .andExpect(status().isNoContent());
    verify(policies).delete(id);
  }

  @Test
  void drainsNodeAndReportsHealth() throws Exception {
    Node node = new Node(new NodeId("node-a"), "n", "r", new NodeCapacity(14, 49152, 500), "fp");
    when(nodes.list()).thenReturn(List.of(node));
    when(admin.listWorkspaces()).thenReturn(List.of());
    mvc.perform(post("/admin/v1/nodes/node-a/drain").principal(principal))
        .andExpect(status().isNoContent());
    verify(nodes).drain(new NodeId("node-a"));
    mvc.perform(get("/admin/v1/health").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes").value(1));
  }

  @Test
  void auditRejectsBadLimit() throws Exception {
    when(audit.latest(0)).thenThrow(new IllegalArgumentException("limit must be positive"));
    mvc.perform(get("/admin/v1/audit/query").principal(principal).param("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
