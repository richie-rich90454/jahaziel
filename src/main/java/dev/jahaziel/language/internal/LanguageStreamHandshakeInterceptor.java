/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.security.api.BrowserOrigin;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
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
    if (!BrowserOrigin.matchesIfPresent(
        request.getHeaders().getOrigin(),
        request.getHeaders().getFirst("X-Forwarded-Proto"),
        request.getHeaders().getFirst("X-Forwarded-Host"),
        request.getHeaders().getFirst("Host"),
        request.getURI())) {
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

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {
    // Authorization is complete; the handler starts the process after the WebSocket opens.
  }
}
