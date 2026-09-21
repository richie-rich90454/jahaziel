/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/** Discovery of {@link PortRouter} with no providers registered. */
class PortRouterDiscoveryTest {
  @Test
  void loadsWithoutProviders() {
    int count =
        assertDoesNotThrow(
            () -> ServiceLoader.load(PortRouter.class).stream().mapToInt(p -> 1).sum());
    assertEquals(0, count);
  }
}
