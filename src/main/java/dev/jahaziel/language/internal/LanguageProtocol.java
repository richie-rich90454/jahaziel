/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import java.io.IOException;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Validates the deliberately small client-to-server LSP surface exposed by the workbench. */
final class LanguageProtocol {
  private static final Set<String> REQUEST_METHODS = Set.of("initialize", "shutdown");
  private static final Set<String> CLIENT_METHODS =
      Set.of(
          "initialize",
          "initialized",
          "shutdown",
          "exit",
          "textDocument/didOpen",
          "textDocument/didChange",
          "textDocument/didSave",
          "textDocument/didClose",
          "$/cancelRequest");

  private LanguageProtocol() {}

  static ObjectNode parseClientMessage(String jsonMessage, ObjectMapper json) throws IOException {
    if (jsonMessage == null) {
      throw new IOException("language-server message must be JSON");
    }
    JsonNode message;
    try {
      message = json.readTree(jsonMessage);
    } catch (JacksonException invalid) {
      throw new IOException("language-server message must be JSON", invalid);
    }
    if (message == null
        || !message.isObject()
        || !"2.0".equals(message.path("jsonrpc").stringValue())) {
      throw new IOException("language-server message must be a JSON-RPC object");
    }
    JsonNode methodNode = message.get("method");
    String method = methodNode == null || !methodNode.isTextual() ? "" : methodNode.stringValue();
    if (method.isEmpty()) {
      if (!message.has("id") || (!message.has("result") && !message.has("error"))) {
        throw new IOException("language-server response is invalid");
      }
    } else if (!CLIENT_METHODS.contains(method)) {
      throw new IOException("language-server method is not allowed");
    } else if (REQUEST_METHODS.contains(method) && !message.has("id")) {
      throw new IOException("language-server request id is required");
    } else if (REQUEST_METHODS.contains(method) && message.get("id").isNull()) {
      throw new IOException("language-server request id must not be null");
    } else if (!REQUEST_METHODS.contains(method) && message.has("id")) {
      throw new IOException("language-server notification must not have an id");
    }
    return ((ObjectNode) message).deepCopy();
  }
}
