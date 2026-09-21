/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Locale;

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
   * Legacy host probe kept for SPI compatibility. New code must use {@code
   * dev.jahaziel.platform.Platforms} instead; no new {@code os.name} checks belong outside that
   * package.
   *
   * @return the current OS family
   * @deprecated use dev.jahaziel.platform.Platforms
   */
  @Deprecated
  public static Os current() {
    String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (name.contains("win")) {
      return WINDOWS;
    }
    if (name.contains("mac") || name.contains("darwin")) {
      return MAC;
    }
    if (name.contains("nix") || name.contains("nux")) {
      return LINUX;
    }
    return OTHER;
  }

  /**
   * Returns whether workspace virtualization can run here.
   *
   * @return true on Linux only
   */
  public boolean supportsVirtualization() {
    return this == LINUX;
  }
}
