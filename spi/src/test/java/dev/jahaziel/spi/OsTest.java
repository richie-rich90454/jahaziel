/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Behavior of {@link Os} detection. */
class OsTest {
  @Test
  void currentMatchesHost() {
    String name = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    Os expected = name.contains("win") ? Os.WINDOWS : Os.OTHER;
    if (name.contains("mac") || name.contains("darwin")) {
      expected = Os.MAC;
    } else if (name.contains("nix") || name.contains("nux") || name.contains("aix")) {
      expected = Os.LINUX;
    }
    assertEquals(expected, Os.current());
  }

  @Test
  void onlyLinuxVirtualizes() {
    assertTrue(Os.LINUX.supportsVirtualization());
    assertFalse(Os.WINDOWS.supportsVirtualization());
    assertFalse(Os.MAC.supportsVirtualization());
    assertFalse(Os.OTHER.supportsVirtualization());
  }
}
