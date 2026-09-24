/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.storage.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of {@link CacheArtifactEntity}. */
class CacheArtifactEntityTest {
  @Test
  void storesAllFields() {
    CacheArtifactEntity entity = new CacheArtifactEntity();
    UUID artifactId = UUID.randomUUID();
    entity.setArtifactId(artifactId);
    entity.setTool("maven");
    entity.setCoordinate("org.example:lib:1.0");
    entity.setSha256("abc");
    entity.setSizeBytes(4096L);
    entity.setPathOnHost("/cache/lib.jar");
    entity.setPopulatedAt(Instant.EPOCH);
    entity.setSource("warmed");
    assertEquals(artifactId, entity.getArtifactId());
    assertEquals("maven", entity.getTool());
    assertEquals("org.example:lib:1.0", entity.getCoordinate());
    assertEquals("abc", entity.getSha256());
    assertEquals(4096L, entity.getSizeBytes());
    assertEquals("/cache/lib.jar", entity.getPathOnHost());
    assertEquals(Instant.EPOCH, entity.getPopulatedAt());
    assertEquals("warmed", entity.getSource());
  }

  @Test
  void equalsByArtifactId() {
    UUID artifactId = UUID.randomUUID();
    CacheArtifactEntity first = new CacheArtifactEntity();
    first.setArtifactId(artifactId);
    first.setTool("a");
    CacheArtifactEntity second = new CacheArtifactEntity();
    second.setArtifactId(artifactId);
    second.setTool("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setArtifactId(UUID.randomUUID());
    assertFalse(first.equals(second));
  }
}
