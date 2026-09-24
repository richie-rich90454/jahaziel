/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.administration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of {@link AdminActionEntity}. */
class AdminActionEntityTest {
  @Test
  void storesAllFields() {
    AdminActionEntity entity = new AdminActionEntity();
    UUID actionId = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    UUID approver = UUID.randomUUID();
    entity.setActionId(actionId);
    entity.setTimestamp(Instant.EPOCH);
    entity.setAdminUserId(admin);
    entity.setAction("workspace.destroy");
    entity.setTarget("ws-1");
    entity.setDetails("{}");
    entity.setReason("abuse");
    entity.setBreakGlass(true);
    entity.setApproverUserId(approver);
    assertEquals(actionId, entity.getActionId());
    assertEquals(Instant.EPOCH, entity.getTimestamp());
    assertEquals(admin, entity.getAdminUserId());
    assertEquals("workspace.destroy", entity.getAction());
    assertEquals("ws-1", entity.getTarget());
    assertEquals("{}", entity.getDetails());
    assertEquals("abuse", entity.getReason());
    assertTrue(entity.isBreakGlass());
    assertEquals(approver, entity.getApproverUserId());
  }

  @Test
  void equalsByActionId() {
    UUID actionId = UUID.randomUUID();
    AdminActionEntity first = new AdminActionEntity();
    first.setActionId(actionId);
    first.setAction("a");
    AdminActionEntity second = new AdminActionEntity();
    second.setActionId(actionId);
    second.setAction("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setActionId(UUID.randomUUID());
    assertFalse(first.equals(second));
  }
}
