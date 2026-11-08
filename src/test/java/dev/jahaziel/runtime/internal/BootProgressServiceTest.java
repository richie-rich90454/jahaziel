/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.runtime.internal.BootProgressService.BootPhase;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link BootProgressService}. */
class BootProgressServiceTest {
  private final BootProgressService service = new BootProgressService();
  private final WorkspaceId id = new WorkspaceId(UUID.randomUUID());

  @Test
  void emptyUntilReported() {
    assertTrue(service.snapshot(id).isEmpty());
    assertTrue(service.snapshotAll().isEmpty());
  }

  @Test
  void reportOverwritesInOrder() {
    service.report(id, BootPhase.PREPARING);
    UUID correlationId = service.snapshot(id).orElseThrow().correlationId();
    service.report(id, BootPhase.STARTING);
    var progress = service.snapshot(id).orElseThrow();
    assertEquals(BootPhase.STARTING, progress.phase());
    assertEquals(correlationId, progress.correlationId());
    assertTrue(service.snapshotAll().get(id.value()).phase() == BootPhase.STARTING);
  }

  @Test
  void clearDropsReport() {
    service.report(id, BootPhase.READY);
    service.clear(id);
    assertTrue(service.snapshot(id).isEmpty());
    service.clear(id);
  }

  @Test
  void guards() {
    assertThrows(NullPointerException.class, () -> service.report(null, BootPhase.READY));
    assertThrows(NullPointerException.class, () -> service.report(id, null));
    assertThrows(NullPointerException.class, () -> service.snapshot(null));
    assertThrows(NullPointerException.class, () -> service.clear(null));
    assertThrows(
        NullPointerException.class,
        () -> new BootProgressService.Progress(null, BootPhase.READY, java.time.Instant.now()));
    assertThrows(
        NullPointerException.class,
        () -> new BootProgressService.Progress(UUID.randomUUID(), BootPhase.READY, null));
  }
}
