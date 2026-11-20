/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jahaziel.terminal.internal.TerminalStreamHandler.StreamMessage;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/** The terminal WebSocket frames must stay JSON round-trippable after the AP CSA refactor. */
class TerminalStreamMessageJsonTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void serializesFrameFields() throws Exception {
        String frame = json.writeValueAsString(new StreamMessage("history", "hello", null, null, null));
        assertEquals("history", json.readTree(frame).get("type").asString());
        assertEquals("hello", json.readTree(frame).get("data").asString());
    }

    @Test
    void deserializesResizeFrame() throws Exception {
        StreamMessage frame =
                json.readValue(
                        "{\"type\":\"resize\",\"rows\":24,\"cols\":80}", StreamMessage.class);
        assertEquals("resize", frame.type());
        assertEquals(Integer.valueOf(24), frame.rows());
        assertEquals(Integer.valueOf(80), frame.cols());
    }
}
