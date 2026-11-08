/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.debug.api.DebugService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the workspace- and session-bound Debug Adapter Protocol stream. */
@Configuration
public class DebugWebSocketConfig implements WebSocketConfigurer {
  private final AuthorizationService authorization;
  private final DebugService debug;

  public DebugWebSocketConfig(AuthorizationService authorization, DebugService debug) {
    this.authorization = authorization;
    this.debug = debug;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(
            debugStreamHandler(),
            "/api/v1/workspaces/{workspaceId}/debug-sessions/{sessionId}/stream")
        .addInterceptors(new DebugStreamHandshakeInterceptor(authorization, debug));
  }

  @Bean
  public DebugStreamHandler debugStreamHandler() {
    return new DebugStreamHandler(debug);
  }
}
