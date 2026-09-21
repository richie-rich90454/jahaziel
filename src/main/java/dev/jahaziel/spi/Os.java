/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Host operating system. Firecracker and cgroups exist on Linux only; everything else is local. */
public enum Os {
  /** Linux, the production target. */
  LINUX,
  /** Windows and Windows Server. */
  WINDOWS,
  /** macOS. */
  MAC,
  /** Anything else. */
  OTHER;

  /**
   * Returns whether workspace virtualization can run here.
   *
   * @return true on Linux only
   */
  public boolean supportsVirtualization() {
    return this == LINUX;
  }
}
