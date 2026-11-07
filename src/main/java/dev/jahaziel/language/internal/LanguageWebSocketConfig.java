/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.workspace.api.WorkspaceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the same-origin language-service WebSocket route. */
@Configuration
public class LanguageWebSocketConfig implements WebSocketConfigurer {
  private final AuthorizationService authorization;
  private final LanguagePackService packs;
  private final LanguageServerProcessManager processes;
  private final WorkspaceService workspaces;

  public LanguageWebSocketConfig(
      AuthorizationService authorization,
      LanguagePackService packs,
      LanguageServerProcessManager processes,
      WorkspaceService workspaces) {
    this.authorization = authorization;
    this.packs = packs;
    this.processes = processes;
    this.workspaces = workspaces;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(
            languageStreamHandler(),
            "/api/v1/workspaces/{workspaceId}/language-services/{languageId}")
        .addInterceptors(new LanguageStreamHandshakeInterceptor(authorization, packs, workspaces));
  }

  @Bean
  public LanguageStreamHandler languageStreamHandler() {
    return new LanguageStreamHandler(processes);
  }
}
