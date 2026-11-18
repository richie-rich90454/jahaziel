/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.debug.api.DebugService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class DebugStreamHandshakeInterceptorTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final DebugService debug = mock(DebugService.class);
  private final DebugStreamHandshakeInterceptor interceptor =
      new DebugStreamHandshakeInterceptor(authorization, debug);
  private final UserId owner = new UserId(UUID.randomUUID());
  private final WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
  private final UUID sessionId = UUID.randomUUID();
  private ServerHttpRequest request;

  @BeforeEach
  void setUp() {
    request = request("https://ide.example");
    when(authorization.isAllowed(owner, "write", workspace)).thenReturn(true);
    when(debug.get(workspace, sessionId))
        .thenReturn(
            new DebugService.Session(
                sessionId,
                workspace.value(),
                "node",
                "pwa-node",
                "RUNNING",
                "1.0",
                Instant.now(),
                ""));
  }

  @Test
  void bindsAuthorizedSameOriginUserToTheExactWorkspaceAndSession() {
    HashMap<String, Object> attributes = new HashMap<>();

    assertTrue(
        interceptor.beforeHandshake(
            request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes));

    assertTrue(attributes.get(DebugStreamHandshakeInterceptor.WORKSPACE_ID) instanceof WorkspaceId);
    assertTrue(attributes.get(DebugStreamHandshakeInterceptor.SESSION_ID) instanceof UUID);
    verify(debug).get(workspace, sessionId);
  }

  @Test
  void rejectsCrossOriginBeforeResolvingAWorkspaceSession() {
    request = request("https://attacker.example");

    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));

    verify(debug, never()).get(workspace, sessionId);
  }

  @Test
  void rejectsDeniedAndStoppedDebugSessions() {
    when(authorization.isAllowed(owner, "write", workspace)).thenReturn(false);
    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));
    verify(debug, never()).get(workspace, sessionId);

    when(authorization.isAllowed(owner, "write", workspace)).thenReturn(true);
    when(debug.get(workspace, sessionId))
        .thenReturn(
            new DebugService.Session(
                sessionId,
                workspace.value(),
                "node",
                "pwa-node",
                "STOPPED",
                "1.0",
                Instant.now(),
                ""));
    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));
  }

  @Test
  void rejectsAStatusThatDoesNotMatchTheAuthorizedWorkspace() {
    when(debug.get(workspace, sessionId))
        .thenReturn(
            new DebugService.Session(
                sessionId,
                UUID.randomUUID(),
                "node",
                "pwa-node",
                "RUNNING",
                "1.0",
                Instant.now(),
                ""));

    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));
  }

  private ServerHttpRequest request(String origin) {
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Host", "ide.example");
    headers.add("Origin", origin);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getURI())
        .thenReturn(
            URI.create(
                "https://ide.example/api/v1/workspaces/"
                    + workspace.value()
                    + "/debug-sessions/"
                    + sessionId
                    + "/stream"));
    when(request.getPrincipal()).thenReturn(() -> owner.value().toString());
    return request;
  }
}
