/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.debug.api.DebugService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.security.api.BrowserOrigin;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Binds a DAP socket to one authenticated user, workspace, and live debugger session. */
public final class DebugStreamHandshakeInterceptor implements HandshakeInterceptor {
  static final String WORKSPACE_ID = "jahaziel.debug.workspace-id";
  static final String SESSION_ID = "jahaziel.debug.session-id";

  private final AuthorizationService authorization;
  private final DebugService debug;

  public DebugStreamHandshakeInterceptor(AuthorizationService authorization, DebugService debug) {
    this.authorization = authorization;
    this.debug = debug;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {
    if (request.getPrincipal() == null
        || !BrowserOrigin.matchesIfPresent(
            request.getHeaders().getOrigin(),
            request.getHeaders().getFirst("X-Forwarded-Proto"),
            request.getHeaders().getFirst("X-Forwarded-Host"),
            request.getHeaders().getFirst("Host"),
            request.getURI())) {
      return false;
    }
    try {
      String[] path = request.getURI().getPath().split("/");
      if (path.length != 8
          || !path[1].equals("api")
          || !path[2].equals("v1")
          || !path[3].equals("workspaces")
          || !path[5].equals("debug-sessions")
          || !path[7].equals("stream")) {
        return false;
      }
      UserId actor = new UserId(UUID.fromString(request.getPrincipal().getName()));
      WorkspaceId workspace = new WorkspaceId(UUID.fromString(path[4]));
      UUID session = UUID.fromString(path[6]);
      if (!authorization.isAllowed(actor, "write", workspace)) return false;
      DebugService.Session status = debug.get(workspace, session);
      if (!status.workspaceId().equals(workspace.value()) || !status.state().equals("RUNNING")) {
        return false;
      }
      attributes.put(WORKSPACE_ID, workspace);
      attributes.put(SESSION_ID, session);
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
    // The handshake binds authorization to the exact workspace and session before streaming.
  }
}
