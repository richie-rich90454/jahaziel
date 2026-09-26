/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.ide.internal.IdeServerService.BootPhase;
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
    service.report(id, BootPhase.EXTRACTING);
    var progress = service.snapshot(id).orElseThrow();
    assertEquals(BootPhase.EXTRACTING, progress.phase());
    assertTrue(service.snapshotAll().get(id.value()).phase() == BootPhase.EXTRACTING);
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
        () -> new BootProgressService.Progress(null, java.time.Instant.now()));
    assertThrows(
        NullPointerException.class, () -> new BootProgressService.Progress(BootPhase.READY, null));
  }
}
