/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LanguageProtocolTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void permitsOnlyDocumentSyncRequestsAndTheirResponses() throws Exception {
    assertEquals(
        "textDocument/didOpen",
        LanguageProtocol.parseClientMessage(
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{}}", json)
            .get("method")
            .stringValue());
    assertEquals(
        4,
        LanguageProtocol.parseClientMessage("{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":null}", json)
            .get("id")
            .intValue());
  }

  @Test
  void rejectsBrowserSelectedCommandsUnknownMethodsAndInvalidRequestIds() {
    assertThrows(
        IOException.class,
        () ->
            LanguageProtocol.parseClientMessage(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"workspace/executeCommand\",\"params\":{}}",
                json));
    assertThrows(
        IOException.class,
        () ->
            LanguageProtocol.parseClientMessage(
                "{\"jsonrpc\":\"2.0\",\"method\":\"workspace/symbol\",\"params\":{}}", json));
    assertThrows(
        IOException.class,
        () ->
            LanguageProtocol.parseClientMessage(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"initialize\",\"params\":{}}", json));
  }
}
