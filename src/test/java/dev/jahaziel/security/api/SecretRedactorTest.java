/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecretRedactorTest {
  @Test
  void redactsAssignmentsBearerTokensAndJwtValues() {
    String text =
        "token=abc123 password: 'open sesame' Authorization: Bearer opaque-token "
            + "eyJabcdefghijk.abcdefghijk.abcdefghijk";

    assertEquals(
        "token=[redacted] password: [redacted] Authorization: [redacted] [redacted token]",
        SecretRedactor.redact(text));
  }

  @Test
  void redactsAssignmentsBearerTokensAndJwtsSplitAcrossStreamChunks() {
    SecretRedactor.Stream stream = new SecretRedactor.Stream();
    StringBuilder output = new StringBuilder();

    output.append(stream.accept("token="));
    output.append(stream.accept("split-"));
    output.append(stream.accept("value\nBearer "));
    output.append(stream.accept("split.bearer"));
    output.append(stream.accept("-value\nJWT eyJabcdefgh."));
    output.append(stream.accept("ijklmnop."));
    output.append(stream.accept("qrstuvwx\n"));
    output.append(stream.finish());

    assertEquals("token=[redacted]\nBearer [redacted]\nJWT [redacted token]\n", output.toString());
    assertFalse(output.toString().contains("split-value"));
    assertFalse(output.toString().contains("split.bearer-value"));
    assertFalse(output.toString().contains("eyJabcdefgh"));
  }

  @Test
  void boundsPendingSecretBufferAndSuppressesFurtherOutputFailClosed() {
    SecretRedactor.Stream stream = new SecretRedactor.Stream();
    String oversizedSecret = "token=" + "s".repeat(64 * 1024 + 1);

    String output = stream.accept(oversizedSecret);

    assertTrue(output.contains("output suppressed after an oversized secret-like value"));
    assertFalse(output.contains("s".repeat(128)));
    assertEquals("", stream.accept("more output after the oversized value"));
    assertEquals("", stream.finish());
  }
}
