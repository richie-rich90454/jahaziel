/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * End-to-end acceptance: log in, get a workspace, open ports, delete. Needs JAHAZIEL_E2E_BASE_URL
 * and JAHAZIEL_E2E_TOKEN; skipped otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JahazielE2eTest {
  private E2eClient client;

  /** Wires the client or skips the suite. */
  @BeforeAll
  void setUp() {
    String baseUrl = System.getenv("JAHAZIEL_E2E_BASE_URL");
    String token = System.getenv("JAHAZIEL_E2E_TOKEN");
    assumeTrue(baseUrl != null && !baseUrl.isBlank(), "no platform under test");
    assumeTrue(token != null && !token.isBlank(), "no token under test");
    client = new E2eClient(baseUrl, token);
  }

  @Test
  void loginReturnsCurrentUser() {
    assertTrue(client.me().contains("email"));
  }

  @Test
  void workspaceLifecycle() {
    String id = client.createWorkspace("e2e-" + System.currentTimeMillis());
    try {
      assertTrue(client.workspace(id).contains(id));
      assertTrue(client.myWorkspaces().contains(id));
      assertFalse(client.ports(id).isBlank());
    } finally {
      client.deleteWorkspace(id);
    }
  }
}
