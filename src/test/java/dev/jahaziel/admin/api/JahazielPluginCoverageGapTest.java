/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JahazielPluginCoverageGapTest {
  static final class TrivialPlugin implements JahazielPlugin {}

  @Test
  void defaultsDoNothing() {
    JahazielPlugin plugin = new TrivialPlugin();
    assertEquals("TrivialPlugin", plugin.name());
    plugin.onStartup();
    plugin.onShutdown();
    assertTrue(plugin.authenticate("token").isEmpty());
    assertTrue(plugin.gatewayTarget(UUID.randomUUID(), 8080).isEmpty());
    plugin.adminAction("restart", Map.of("reason", "test"));
  }
}
