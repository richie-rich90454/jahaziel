/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.security.api.BrowserOrigin;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Binds a WebSocket upgrade to an authenticated user and an authorized terminal session. */
public final class TerminalStreamHandshakeInterceptor implements HandshakeInterceptor {
  static final String SESSION_ID = "jahaziel.terminal.session-id";

  private final TerminalService terminals;
  private final AuthorizationService authorization;

  public TerminalStreamHandshakeInterceptor(
      TerminalService terminals, AuthorizationService authorization) {
    this.terminals = terminals;
    this.authorization = authorization;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {
    if (request.getPrincipal() == null) {
      return false;
    }
    if (!BrowserOrigin.matchesIfPresent(
        request.getHeaders().getOrigin(),
        request.getHeaders().getFirst("X-Forwarded-Proto"),
        request.getHeaders().getFirst("X-Forwarded-Host"),
        request.getHeaders().getFirst("Host"),
        request.getURI())) {
      return false;
    }
    try {
      String[] segments = request.getURI().getPath().split("/");
      UUID sessionUuid = UUID.fromString(segments[segments.length - 2]);
      var session = terminals.get(new TerminalSessionId(sessionUuid));
      UserId actor = new UserId(UUID.fromString(request.getPrincipal().getName()));
      if (!authorization.isAllowed(actor, "write", session.workspaceId())) {
        return false;
      }
      attributes.put(SESSION_ID, sessionUuid);
      attributes.put("jahaziel.terminal.workspace-id", session.workspaceId());
      return true;
    } catch (RuntimeException invalidOrDenied) {
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {
    // No additional state is needed after authorization.
  }
}
