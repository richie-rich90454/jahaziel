/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** WebSocket terminal input and output transport. */
public final class TerminalStreamHandler extends TextWebSocketHandler {
  private final TerminalService terminals;
  private final ObjectMapper json;
  private final ConcurrentMap<String, AutoCloseable> subscriptions = new ConcurrentHashMap<>();

  public TerminalStreamHandler(TerminalService terminals, ObjectMapper json) {
    this.terminals = terminals;
    this.json = json;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    TerminalSessionId id = terminalId(session);
    AutoCloseable subscription =
        terminals.attach(
            id,
            event ->
                send(
                    session,
                    new StreamMessage(event.type(), event.data(), null, null, event.exitCode())));
    subscriptions.put(session.getId(), subscription);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    TerminalSessionId id = terminalId(session);
    if (message.getPayloadLength() > 20 * 1024) {
      session.close(CloseStatus.TOO_BIG_TO_PROCESS);
      return;
    }
    try {
      StreamMessage input = json.readValue(message.getPayload(), StreamMessage.class);
      switch (input.type()) {
        case "input" -> terminals.write(id, input.data() == null ? "" : input.data());
        case "resize" -> terminals.resize(id, integer(input.rows()), integer(input.cols()));
        default -> session.close(CloseStatus.BAD_DATA);
      }
      terminals.activity(id);
    } catch (JacksonException | IllegalArgumentException invalid) {
      session.close(CloseStatus.BAD_DATA);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    AutoCloseable subscription = subscriptions.remove(session.getId());
    if (subscription != null) {
      try {
        subscription.close();
      } catch (Exception ignored) {
        // Disconnect cleanup must not affect the process or other subscribers.
      }
    }
  }

  private void send(WebSocketSession session, StreamMessage message) {
    if (!session.isOpen()) {
      return;
    }
    synchronized (session) {
      try {
        session.sendMessage(new TextMessage(json.writeValueAsString(message)));
      } catch (IOException | JacksonException failure) {
        try {
          session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
          // The peer is already gone.
        }
      }
    }
  }

  private static int integer(Integer value) {
    if (value == null) {
      throw new IllegalArgumentException("terminal resize dimensions are required");
    }
    return value;
  }

  private static TerminalSessionId terminalId(WebSocketSession session) {
    Object value = session.getAttributes().get(TerminalStreamHandshakeInterceptor.SESSION_ID);
    if (!(value instanceof UUID id)) {
      throw new IllegalStateException("terminal session was not authorized at handshake");
    }
    return new TerminalSessionId(id);
  }

  public record StreamMessage(
      String type, String data, Integer rows, Integer cols, Integer exitCode) {}
}
