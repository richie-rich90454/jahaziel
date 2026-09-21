/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Behavior of {@link Os} detection. */
class OsTest {
  @Test
  void onlyLinuxVirtualizes() {
    assertTrue(Os.LINUX.supportsVirtualization());
    assertFalse(Os.WINDOWS.supportsVirtualization());
    assertFalse(Os.MAC.supportsVirtualization());
    assertFalse(Os.OTHER.supportsVirtualization());
  }
}
