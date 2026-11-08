/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import dev.jahaziel.debug.api.DebugService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Streams bounded DAP JSON messages to one previously authorized debugger session. */
public final class DebugStreamHandler extends TextWebSocketHandler {
  private static final int MAX_MESSAGE_CHARS = 1024 * 1024;

  private final DebugService debug;
  private final ConcurrentMap<String, AutoCloseable> attachments = new ConcurrentHashMap<>();

  public DebugStreamHandler(DebugService debug) {
    this.debug = debug;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    session.setTextMessageSizeLimit(MAX_MESSAGE_CHARS);
    try {
      AutoCloseable attachment =
          debug.attach(workspaceId(session), sessionId(session), message -> send(session, message));
      attachments.put(session.getId(), attachment);
    } catch (IllegalStateException unavailable) {
      session.close(new CloseStatus(1013, "debug session is no longer running"));
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
    if (!attachments.containsKey(session.getId())) {
      session.close(CloseStatus.SERVER_ERROR);
      return;
    }
    try {
      debug.send(workspaceId(session), sessionId(session), message.getPayload());
    } catch (IllegalArgumentException invalid) {
      session.close(CloseStatus.BAD_DATA);
    } catch (IllegalStateException unavailable) {
      session.close(new CloseStatus(1013, "debug session is no longer running"));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    AutoCloseable attachment = attachments.remove(session.getId());
    if (attachment != null) {
      try {
        attachment.close();
      } catch (Exception ignored) {
        // The debug process is owned by the debugger session, not by this reconnectable socket.
      }
    }
  }

  private static void send(WebSocketSession session, String jsonMessage) {
    if (!session.isOpen()) return;
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
    Object value = session.getAttributes().get(DebugStreamHandshakeInterceptor.WORKSPACE_ID);
    if (value instanceof WorkspaceId id) return id;
    throw new IllegalStateException("debug workspace was not authorized at handshake");
  }

  private static UUID sessionId(WebSocketSession session) {
    Object value = session.getAttributes().get(DebugStreamHandshakeInterceptor.SESSION_ID);
    if (value instanceof UUID id) return id;
    throw new IllegalStateException("debug session was not authorized at handshake");
  }
}
