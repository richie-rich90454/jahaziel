/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validation of {@link PrepareRequest}. */
class PrepareRequestTest {
  @Test
  void storesFields() {
    PrepareRequest request =
        new PrepareRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ImageRef("base-image:2026.09"),
            new QuotaBytes(1),
            2,
            2048);
    assertEquals(2, request.vcpuCount());
    assertEquals(2048, request.memoryMib());
  }

  @Test
  void rejectsBadSizing() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PrepareRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ImageRef("x"),
                new QuotaBytes(1),
                0,
                2048));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PrepareRequest(
                UUID.randomUUID(), UUID.randomUUID(), new ImageRef("x"), new QuotaBytes(1), 2, 0));
  }
}
