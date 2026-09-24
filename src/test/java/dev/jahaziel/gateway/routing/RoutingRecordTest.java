/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Edge cases of host parsing and the routing table guards. */
class RoutingRecordTest {
  @Test
  void parsedHostGuardsInputs() {
    ParsedHost parsed = new ParsedHost("richard", 8080);
    assertEquals("richard", parsed.userSlug());
    assertEquals(8080, parsed.port());
    assertThrows(IllegalArgumentException.class, () -> new ParsedHost(null, 8080));
    assertThrows(IllegalArgumentException.class, () -> new ParsedHost("  ", 8080));
    assertThrows(IllegalArgumentException.class, () -> new ParsedHost("richard", 0));
    assertThrows(IllegalArgumentException.class, () -> new ParsedHost("richard", 65536));
  }

  @Test
  void hostParserGuardsDomain() {
    assertThrows(IllegalArgumentException.class, () -> new HostParser(null));
    assertThrows(IllegalArgumentException.class, () -> new HostParser("  "));
  }

  @Test
  void hostParserRejectsTrailingDash() {
    HostParser parser = new HostParser("code.example.com");
    assertThrows(IllegalArgumentException.class, () -> parser.parse("richard-.code.example.com"));
  }

  @Test
  void directoryGuardsSlugs() {
    WorkspaceDirectory directory = new WorkspaceDirectory();
    UUID workspace = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> directory.register(null, 8080, workspace));
    assertThrows(IllegalArgumentException.class, () -> directory.register("  ", 8080, workspace));
    assertThrows(IllegalArgumentException.class, () -> directory.lookup(null, 8080));
    assertThrows(IllegalArgumentException.class, () -> directory.lookup("  ", 8080));
    assertThrows(IllegalArgumentException.class, () -> directory.unregister(null, 8080));
    assertThrows(IllegalArgumentException.class, () -> directory.unregister("  ", 8080));
    directory.register("richard", 8080, workspace);
    assertTrue(directory.lookup("richard", 8080).isPresent());
  }
}
