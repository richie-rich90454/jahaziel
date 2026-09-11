/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Logical storage quota in bytes. Only actual consumption matters physically. */
public record QuotaBytes(long value) {
  public QuotaBytes {
    if (value < 0) {
      throw new IllegalArgumentException("value must not be negative");
    }
  }
}
