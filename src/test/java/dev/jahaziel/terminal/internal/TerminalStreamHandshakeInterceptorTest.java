/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.net.URI;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class TerminalStreamHandshakeInterceptorTest {
  private final TerminalService terminals = mock(TerminalService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final TerminalStreamHandshakeInterceptor interceptor =
      new TerminalStreamHandshakeInterceptor(terminals, authorization);
  private final UserId owner = new UserId(UUID.randomUUID());
  private final WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
  private final TerminalSessionId sessionId = new TerminalSessionId(UUID.randomUUID());
  private ServerHttpRequest request;

  @BeforeEach
  void setUp() {
    request = request("http://localhost:8080", owner.value().toString());
    when(terminals.get(sessionId))
        .thenReturn(new TerminalSession(sessionId, workspaceId, owner, 30, 120));
    when(authorization.isAllowed(owner, "write", workspaceId)).thenReturn(true);
  }

  @Test
  void bindsAnAuthorizedSameOriginWorkspaceAndSession() {
    HashMap<String, Object> attributes = new HashMap<>();

    assertTrue(
        interceptor.beforeHandshake(
            request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes));

    assertEquals(sessionId.value(), attributes.get(TerminalStreamHandshakeInterceptor.SESSION_ID));
    assertEquals(workspaceId, attributes.get("jahaziel.terminal.workspace-id"));
    verify(authorization).isAllowed(owner, "write", workspaceId);
  }

  @Test
  void rejectsAnonymousAndCrossOriginRequestsBeforeResolvingASession() {
    request = request("http://localhost:8080", null);
    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));

    request = request("https://attacker.example", owner.value().toString());
    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));

    verify(terminals, never()).get(any(TerminalSessionId.class));
  }

  @Test
  void rejectsAWorkspaceWriterWithoutTerminalWriteAccess() {
    when(authorization.isAllowed(owner, "write", workspaceId)).thenReturn(false);

    assertFalse(
        interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            new HashMap<>()));

    verify(terminals).get(sessionId);
  }

  private ServerHttpRequest request(String origin, String principal) {
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Host", "localhost:8080");
    if (origin != null) headers.add("Origin", origin);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getURI())
        .thenReturn(
            URI.create("http://localhost:8080/api/v1/terminals/" + sessionId.value() + "/stream"));
    if (principal != null) when(request.getPrincipal()).thenReturn(() -> principal);
    return request;
  }
}
