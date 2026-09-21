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
  private final HostParser parser = new HostParser("code.example.com");

  @Test
  void parsesUserAndPort() {
    ParsedHost parsed = parser.parse("richard-8080.code.example.com");
    assertEquals("richard", parsed.userSlug());
    assertEquals(8080, parsed.port());
  }

  @Test
  void splitsAtLastDash() {
    ParsedHost parsed = parser.parse("mary-jane-9000.code.example.com");
    assertEquals("mary-jane", parsed.userSlug());
    assertEquals(9000, parsed.port());
  }

  @Test
  void acceptsUppercaseAndTrailingDot() {
    ParsedHost parsed = parser.parse("RICHARD-8080.CODE.EXAMPLE.COM.");
    assertEquals("richard", parsed.userSlug());
    assertEquals(8080, parsed.port());
  }

  @Test
  void rejectsForeignAndMalformedHosts() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("richard-8080.evil.dev"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("richard.code.example.com"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("richard-x.code.example.com"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("-8080.code.example.com"));
    assertThrows(NullPointerException.class, () -> parser.parse(null));
  }
}
