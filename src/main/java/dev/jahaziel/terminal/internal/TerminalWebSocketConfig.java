/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.terminal.api.TerminalService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.ObjectMapper;

/** Registers the same-origin authenticated terminal stream endpoint. */
@Configuration
@EnableWebSocket
public class TerminalWebSocketConfig implements WebSocketConfigurer {
  private final TerminalService terminals;
  private final AuthorizationService authorization;
  private final ObjectMapper json;

  public TerminalWebSocketConfig(
      TerminalService terminals, AuthorizationService authorization, ObjectMapper json) {
    this.terminals = terminals;
    this.authorization = authorization;
    this.json = json;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(terminalStreamHandler(), "/api/v1/terminals/{sessionId}/stream")
        .addInterceptors(new TerminalStreamHandshakeInterceptor(terminals, authorization));
  }

  @Bean
  public TerminalStreamHandler terminalStreamHandler() {
    return new TerminalStreamHandler(terminals, json);
  }
}
