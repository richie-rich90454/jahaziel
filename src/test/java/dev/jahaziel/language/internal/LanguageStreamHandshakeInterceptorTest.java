/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class LanguageStreamHandshakeInterceptorTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final LanguagePackService packs = mock(LanguagePackService.class);
  private final WorkspaceService workspaces = mock(WorkspaceService.class);
  private final LanguageStreamHandshakeInterceptor interceptor =
      new LanguageStreamHandshakeInterceptor(authorization, packs, workspaces);
  private final UserId owner = new UserId(UUID.randomUUID());
  private final WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
  private final Workspace workspace = readyWorkspace();
  private ServerHttpRequest request;

  @BeforeEach
  void setUp() {
    request = request("http://localhost:8080");
    when(authorization.isAllowed(owner, "write", workspaceId)).thenReturn(true);
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    when(packs.artifactFor(workspaceId, "rust")).thenReturn(Path.of("/managed/rust-analyzer"));
  }

  @Test
  void bindsAnAuthorizedSameOriginWorkspaceAndSelectedPack() {
    HashMap<String, Object> attributes = new HashMap<>();

    assertTrue(
        interceptor.beforeHandshake(
            request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes));

    assertTrue(
        attributes.get(LanguageStreamHandshakeInterceptor.WORKSPACE_ID) instanceof WorkspaceId);
    verify(packs).artifactFor(workspaceId, "rust");
  }

  @Test
  void rejectsCrossOriginRequestsBeforeStartingLanguageProcesses() {
    request = request("https://attacker.example");

    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));

    verify(packs, never()).artifactFor(workspaceId, "rust");
  }

  @Test
  void acceptsPublicHttpsOriginBehindTlsTerminatingProxy() {
    request = request("https://ide.example");
    request.getHeaders().add("X-Forwarded-Proto", "https");
    request.getHeaders().add("X-Forwarded-Host", "ide.example");

    assertTrue(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));

    verify(packs).artifactFor(workspaceId, "rust");
  }

  @Test
  void rejectsUnauthorizedAndStoppedWorkspaces() {
    when(authorization.isAllowed(owner, "write", workspaceId)).thenReturn(false);
    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));
    verify(workspaces, never()).get(workspaceId);

    when(authorization.isAllowed(owner, "write", workspaceId)).thenReturn(true);
    workspace.start();
    workspace.suspend();
    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));
    verify(packs, never()).artifactFor(workspaceId, "rust");
  }

  private ServerHttpRequest request(String origin) {
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Host", "localhost:8080");
    headers.add("Origin", origin);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getURI())
        .thenReturn(
            URI.create(
                "http://localhost:8080/api/v1/workspaces/"
                    + workspaceId.value()
                    + "/language-services/rust"));
    when(request.getPrincipal()).thenReturn(() -> owner.value().toString());
    return request;
  }

  private Workspace readyWorkspace() {
    Workspace workspace =
        new Workspace(
            workspaceId,
            owner,
            "project",
            WorkspaceQuota.defaults(),
            new VirtualMachine(
                new VirtualMachineId(UUID.randomUUID()),
                workspaceId,
                new VirtualMachineConfiguration(2, 2048)));
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    return workspace;
  }
}
