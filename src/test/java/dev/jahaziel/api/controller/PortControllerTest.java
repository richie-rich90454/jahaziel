/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.GatewayEndpoint;
import dev.jahaziel.networking.api.GatewayRouteService;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Endpoints of {@link PortController} with stubbed services. Standalone MockMvc. */
class PortControllerTest {
  private MockMvc mvc;

  private PortService ports;

  private UserService users;

  private AuthorizationService auth;

  private GatewayRouteService routes;

  private JwtService tokens;

  private final Principal principal = () -> UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    ports = mock(PortService.class);
    users = mock(UserService.class);
    auth = mock(AuthorizationService.class);
    routes = mock(GatewayRouteService.class);
    tokens = mock(JwtService.class);
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new PortController(
                    ports,
                    users,
                    auth,
                    routes,
                    tokens,
                    new GatewayEndpoint("https", "code.test", 443),
                    900))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void listsPortsWithUrls() throws Exception {
    UUID workspace = UUID.randomUUID();
    UUID user = UUID.randomUUID();
    PortBinding binding =
        new PortBinding(user, 8080, workspace, "http", Instant.parse("2026-01-02T03:04:05Z"));
    when(ports.bindingsByWorkspace(new WorkspaceId(workspace))).thenReturn(List.of(binding));
    when(users.slug(new UserId(user))).thenReturn("richard");
    when(tokens.issue(any(), any(), any(), any())).thenReturn("tok");
    mvc.perform(
            get("/api/v1/workspaces/" + workspace + "/ports")
                .principal(principal)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].port").value(8080))
        .andExpect(jsonPath("$[0].url").value("https://richard-8080.code.test/?token=tok"));
  }

  @Test
  void missingWorkspaceIsNotFound() throws Exception {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    when(ports.bindingsByWorkspace(id))
        .thenThrow(new dev.jahaziel.workspace.api.WorkspaceNotFoundException(id));
    mvc.perform(get("/api/v1/workspaces/" + id.value() + "/ports").principal(principal))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
