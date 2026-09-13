/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link StringArrayConverter} round-trips and rejections. */
class StringArrayConverterTest {
  private final StringArrayConverter converter = new StringArrayConverter();

  @Test
  void roundTripsValues() {
    String[] values = {"ls", "-la", "quote\"back\\slash", "line\nbreak", "tab\there", "ünïcodé"};
    assertArrayEquals(
        values, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(values)));
  }

  @Test
  void emptyAndNull() {
    assertEquals("[]", converter.convertToDatabaseColumn(new String[0]));
    assertArrayEquals(new String[0], converter.convertToEntityAttribute("[]"));
    assertNull(converter.convertToDatabaseColumn(null));
    assertNull(converter.convertToEntityAttribute(null));
  }

  @Test
  void rejectsMalformed() {
    assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("nope"));
    assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[\"a]"));
    assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[a]"));
  }
}
