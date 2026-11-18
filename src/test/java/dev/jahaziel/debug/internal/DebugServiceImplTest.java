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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebugServiceImplTest {
  @TempDir Path temporaryDirectory;

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

  @Test
  void normalizesAdapterSourcePathsAndHidesPathsOutsideTheWorkspace() throws IOException {
    Path home = temporaryDirectory.resolve("workspace");
    Path source = home.resolve("src/main.js");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "console.log('ok');");

    assertEquals(
        "src/main.js", DebugServiceImpl.workspaceRelativeSourcePath(home, source.toString()));
    assertEquals("src/main.js", DebugServiceImpl.workspaceRelativeSourcePath(home, "src/main.js"));
    assertNull(
        DebugServiceImpl.workspaceRelativeSourcePath(
            home, temporaryDirectory.resolve("secret.txt").toString()));
    assertNull(DebugServiceImpl.workspaceRelativeSourcePath(home, "../../secret.txt"));
  }

  private static ByteArrayInputStream ascii(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
  }
}
