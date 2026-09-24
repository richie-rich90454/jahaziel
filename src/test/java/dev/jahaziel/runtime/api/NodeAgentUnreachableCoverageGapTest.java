/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class NodeAgentUnreachableCoverageGapTest {
  @Test
  void bothConstructors() {
    var simple = new NodeAgentUnreachableException("down");
    assertEquals("down", simple.getMessage());
    assertNull(simple.getCause());
    var cause = new IOException("conn refused");
    var chained = new NodeAgentUnreachableException("down", cause);
    assertEquals("down", chained.getMessage());
    assertSame(cause, chained.getCause());
  }
}
