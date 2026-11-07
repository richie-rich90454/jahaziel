/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Binds a language-service WebSocket to one authorized, selected workspace language. */
public final class LanguageStreamHandshakeInterceptor implements HandshakeInterceptor {
  static final String WORKSPACE_ID = "jahaziel.language.workspace-id";
  static final String LANGUAGE_ID = "jahaziel.language.language-id";

  private final AuthorizationService authorization;
  private final LanguagePackService packs;
  private final WorkspaceService workspaces;

  public LanguageStreamHandshakeInterceptor(
      AuthorizationService authorization, LanguagePackService packs, WorkspaceService workspaces) {
    this.authorization = authorization;
    this.packs = packs;
    this.workspaces = workspaces;
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
      String[] path = request.getURI().getPath().split("/");
      if (path.length != 7
          || !path[1].equals("api")
          || !path[2].equals("v1")
          || !path[3].equals("workspaces")
          || !path[5].equals("language-services")) {
        return false;
      }
      UserId actor = new UserId(UUID.fromString(request.getPrincipal().getName()));
      WorkspaceId workspace = new WorkspaceId(UUID.fromString(path[4]));
      String languageId = path[6];
      if (!authorization.isAllowed(actor, "write", workspace)) {
        return false;
      }
      WorkspaceState state = workspaces.get(workspace).state();
      if (!(state instanceof WorkspaceState.Ready
          || state instanceof WorkspaceState.Active
          || state instanceof WorkspaceState.Idle)) {
        return false;
      }
      packs.artifactFor(workspace, languageId);
      attributes.put(WORKSPACE_ID, workspace);
      attributes.put(LANGUAGE_ID, languageId);
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
    // Authorization is complete; the handler starts the process after the WebSocket opens.
  }
}
