/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of {@link SnapshotEntity}. */
class SnapshotEntityTest {
  @Test
  void storesAllFields() {
    SnapshotEntity entity = new SnapshotEntity();
    UUID snapshotId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    UUID parent = UUID.randomUUID();
    entity.setSnapshotId(snapshotId);
    entity.setWorkspaceId(workspaceId);
    entity.setTag("daily");
    entity.setKind("MANUAL");
    entity.setCreatedAt(Instant.EPOCH);
    entity.setCreatedBy("user");
    entity.setSnapshotPath("/data/snaps/1");
    entity.setLogicalBytes(10L);
    entity.setPhysicalBytes(8L);
    entity.setParentSnapshotId(parent);
    entity.setSha256OfManifest("abc");
    entity.setDeletedAt(Instant.now());
    assertEquals(snapshotId, entity.getSnapshotId());
    assertEquals(workspaceId, entity.getWorkspaceId());
    assertEquals("daily", entity.getTag());
    assertEquals("MANUAL", entity.getKind());
    assertEquals(Instant.EPOCH, entity.getCreatedAt());
    assertEquals("user", entity.getCreatedBy());
    assertEquals("/data/snaps/1", entity.getSnapshotPath());
    assertEquals(10L, entity.getLogicalBytes());
    assertEquals(8L, entity.getPhysicalBytes());
    assertEquals(parent, entity.getParentSnapshotId());
    assertEquals("abc", entity.getSha256OfManifest());
  }

  @Test
  void equalsBySnapshotId() {
    UUID snapshotId = UUID.randomUUID();
    SnapshotEntity first = new SnapshotEntity();
    first.setSnapshotId(snapshotId);
    first.setTag("a");
    SnapshotEntity second = new SnapshotEntity();
    second.setSnapshotId(snapshotId);
    second.setTag("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setSnapshotId(UUID.randomUUID());
    assertFalse(first.equals(second));
  }
}
