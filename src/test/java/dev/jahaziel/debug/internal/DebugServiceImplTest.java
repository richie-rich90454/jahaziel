/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DebugServiceImplTest {
  @Test
  void readsContentLengthFramedUtf8Messages() throws Exception {
    String payload = "{\"type\":\"event\",\"event\":\"output\",\"body\":\"✓\"}";
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    byte[] header =
        ("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
    byte[] frame = new byte[header.length + bytes.length];
    System.arraycopy(header, 0, frame, 0, header.length);
    System.arraycopy(bytes, 0, frame, header.length, bytes.length);

    assertEquals(payload, DebugServiceImpl.readDapMessage(new ByteArrayInputStream(frame)));
  }

  @Test
  void returnsNullOnlyForCleanEndOfStream() throws Exception {
    assertNull(DebugServiceImpl.readDapMessage(new ByteArrayInputStream(new byte[0])));
    assertThrows(
        IOException.class,
        () ->
            DebugServiceImpl.readDapMessage(
                new ByteArrayInputStream(
                    "Content-Length: 2\r\n".getBytes(StandardCharsets.US_ASCII))));
  }

  @Test
  void rejectsDuplicateAndOversizedContentLengths() {
    assertThrows(
        IOException.class,
        () ->
            DebugServiceImpl.readDapMessage(
                ascii("Content-Length: 1\r\nContent-Length: 1\r\n\r\n{}")));
    assertThrows(
        IOException.class,
        () -> DebugServiceImpl.readDapMessage(ascii("Content-Length: 1048577\r\n\r\n")));
  }

  @Test
  void rejectsIncompleteDapBodies() {
    assertThrows(
        IOException.class,
        () -> DebugServiceImpl.readDapMessage(ascii("Content-Length: 5\r\n\r\n{}")));
  }

  private static ByteArrayInputStream ascii(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
  }
}
