/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditPersistenceCoverageGapTest {
  private final StringArrayConverter converter = new StringArrayConverter();

  @Test
  void processWorkspaceAndEndTimeRoundTrip() {
    AuditProcessEntity entity = new AuditProcessEntity();
    UUID workspace = UUID.randomUUID();
    entity.setWorkspaceId(workspace);
    assertEquals(workspace, entity.getWorkspaceId());
    Instant end = Instant.parse("2026-07-08T09:10:11Z");
    entity.setEndTime(end);
    assertEquals(end, entity.getEndTime());
  }

  @Test
  void converterEncodesCarriageReturnAndControls() {
    String[] values = {"a\rb", "a" + ((char) 1) + "b", "plain"};
    String encoded = converter.convertToDatabaseColumn(values);
    assertTrue(encoded.contains("\\r"));
    assertTrue(encoded.contains("u00"));
    assertArrayEquals(values, converter.convertToEntityAttribute(encoded));
  }

  @Test
  void converterDecodesEscapes() {
    assertArrayEquals(new String[] {"A"}, converter.convertToEntityAttribute("[\"\\u0041\"]"));
    assertArrayEquals(new String[] {"\r"}, converter.convertToEntityAttribute("[\"\\r\"]"));
    assertArrayEquals(new String[] {"a", "b"}, converter.convertToEntityAttribute("[\"a\",\"b\"]"));
    assertArrayEquals(new String[0], converter.convertToEntityAttribute("[ ]"));
  }

  @Test
  void converterRejectsBadEscapesAndSeparators() {
    assertThrows(
        IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[\"\\x\"]"));
    assertThrows(
        IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[\"\\u12\"]"));
    assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[,]"));
    assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[\"a]"));
    assertThrows(
        IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[\"a\""));
  }
}
