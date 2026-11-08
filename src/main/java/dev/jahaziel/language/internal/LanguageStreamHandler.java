/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Streams JSON-RPC messages to a fixed server-managed language process. */
public final class LanguageStreamHandler extends TextWebSocketHandler {
  private static final int MAX_MESSAGE_CHARS = 1024 * 1024;

  private final LanguageServerProcessManager processes;
  private final ConcurrentMap<String, LanguageServerProcessManager.Session> sessions =
      new ConcurrentHashMap<>();

  public LanguageStreamHandler(LanguageServerProcessManager processes) {
    this.processes = processes;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    session.setTextMessageSizeLimit(MAX_MESSAGE_CHARS);
    WorkspaceId workspace = workspaceId(session);
    String languageId = languageId(session);
    try {
      LanguageServerProcessManager.Session process =
          processes.open(workspace, languageId, message -> send(session, message));
      sessions.put(session.getId(), process);
    } catch (LanguageSessionLimitException limit) {
      session.close(new CloseStatus(1013, "language-service limit reached"));
    } catch (RuntimeException failed) {
      session.close(CloseStatus.SERVER_ERROR);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    if (message.getPayloadLength() > MAX_MESSAGE_CHARS) {
      session.close(CloseStatus.TOO_BIG_TO_PROCESS);
      return;
    }
    LanguageServerProcessManager.Session process = sessions.get(session.getId());
    if (process == null) {
      session.close(CloseStatus.SERVER_ERROR);
      return;
    }
    try {
      process.send(message.getPayload());
    } catch (IllegalArgumentException invalid) {
      session.close(CloseStatus.BAD_DATA);
    } catch (IOException unavailable) {
      session.close(new CloseStatus(1008, "invalid language-service message"));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    LanguageServerProcessManager.Session process = sessions.remove(session.getId());
    if (process != null) {
      process.close();
    }
  }

  private static void send(WebSocketSession session, String jsonMessage) {
    if (!session.isOpen()) {
      return;
    }
    synchronized (session) {
      try {
        session.sendMessage(new TextMessage(jsonMessage));
      } catch (IOException failure) {
        try {
          session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
          // The peer has already disconnected.
        }
      }
    }
  }

  private static WorkspaceId workspaceId(WebSocketSession session) {
    Object value = session.getAttributes().get(LanguageStreamHandshakeInterceptor.WORKSPACE_ID);
    if (value instanceof WorkspaceId id) {
      return id;
    }
    throw new IllegalStateException("language-service workspace was not authorized at handshake");
  }

  private static String languageId(WebSocketSession session) {
    Object value = session.getAttributes().get(LanguageStreamHandshakeInterceptor.LANGUAGE_ID);
    if (value instanceof String id && !id.isBlank()) {
      return id;
    }
    throw new IllegalStateException("language id was not authorized at handshake");
  }
}
