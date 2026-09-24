/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityEntitiesCoverageGapTest {
  @Test
  void appUserLastLoginRoundTrip() {
    AppUserEntity entity = new AppUserEntity();
    entity.setUserId(UUID.randomUUID());
    Instant login = Instant.parse("2026-03-04T05:06:07Z");
    entity.setLastLoginAt(login);
    assertEquals(login, entity.getLastLoginAt());
    entity.setLastLoginAt(null);
    assertEquals(null, entity.getLastLoginAt());
  }
}
