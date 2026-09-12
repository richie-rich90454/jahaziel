/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link HostParser}. */
class HostParserTest {
  private final HostParser parser = new HostParser("code.jahaziel.dev");

  @Test
  void parsesUserAndPort() {
    ParsedHost parsed = parser.parse("richard-8080.code.jahaziel.dev");
    assertEquals("richard", parsed.userSlug());
    assertEquals(8080, parsed.port());
  }

  @Test
  void splitsAtLastDash() {
    ParsedHost parsed = parser.parse("mary-jane-9000.code.jahaziel.dev");
    assertEquals("mary-jane", parsed.userSlug());
    assertEquals(9000, parsed.port());
  }

  @Test
  void rejectsForeignAndMalformedHosts() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("richard-8080.evil.dev"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("richard.code.jahaziel.dev"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("richard-x.code.jahaziel.dev"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("-8080.code.jahaziel.dev"));
    assertThrows(NullPointerException.class, () -> parser.parse(null));
  }
}
