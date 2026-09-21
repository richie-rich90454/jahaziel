/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link WorkspaceDirectory}. */
class WorkspaceDirectoryTest {
  @Test
  void registersLooksUpAndRemoves() {
    WorkspaceDirectory directory = new WorkspaceDirectory();
    UUID workspace = UUID.randomUUID();
    directory.register("richard", 8080, workspace);
    assertEquals(workspace, directory.lookup("richard", 8080).orElseThrow());
    directory.unregister("richard", 8080);
    assertTrue(directory.lookup("richard", 8080).isEmpty());
  }
}
