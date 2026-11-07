/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import java.net.URI;
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
    if (!sameOriginIfPresent(request)) {
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

  private static boolean sameOriginIfPresent(ServerHttpRequest request) {
    String origin = request.getHeaders().getOrigin();
    if (origin == null || origin.isBlank()) {
      return true;
    }
    try {
      URI uri = URI.create(origin);
      String hostHeader = request.getHeaders().getFirst("Host");
      URI requestUri = request.getURI();
      String authority =
          hostHeader == null || hostHeader.isBlank() ? requestUri.getAuthority() : hostHeader;
      URI target = URI.create(requestUri.getScheme() + "://" + authority);
      return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          && uri.getUserInfo() == null
          && (uri.getRawPath() == null
              || uri.getRawPath().isEmpty()
              || uri.getRawPath().equals("/"))
          && uri.getQuery() == null
          && uri.getFragment() == null
          && uri.getHost() != null
          && uri.getHost().equalsIgnoreCase(target.getHost())
          && effectivePort(uri) == effectivePort(target);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  private static int effectivePort(URI uri) {
    if (uri.getPort() >= 0) return uri.getPort();
    return "https".equals(uri.getScheme()) ? 443 : 80;
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
