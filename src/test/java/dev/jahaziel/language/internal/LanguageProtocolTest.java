/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

class LanguageProtocolTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void permitsOnlyDocumentSyncRequestsAndTheirResponses() throws Exception {
        assertEquals(
                "textDocument/didOpen",
                LanguageProtocol.parseClientMessage(
                                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{}}",
                                json)
                        .get("method")
                        .stringValue());
        assertEquals(
                4,
                LanguageProtocol.parseClientMessage(
                                "{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":null}", json)
                        .get("id")
                        .intValue());
    }

    @Test
    void rejectsBrowserSelectedCommandsUnknownMethodsAndInvalidRequestIds() {
        assertThrows(
                IOException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        LanguageProtocol.parseClientMessage(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"workspace/executeCommand\",\"params\":{}}",
                                json);
                    }
                });
        assertThrows(
                IOException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        LanguageProtocol.parseClientMessage(
                                "{\"jsonrpc\":\"2.0\",\"method\":\"workspace/symbol\",\"params\":{}}",
                                json);
                    }
                });
        assertThrows(
                IOException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        LanguageProtocol.parseClientMessage(
                                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"initialize\",\"params\":{}}",
                                json);
                    }
                });
    }
}
