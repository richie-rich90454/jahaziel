/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link SnapshotMetadata}. */
class SnapshotMetadataTest {
  @Test
  void manualFactoryBuildsManualSnapshot() {
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    SnapshotMetadata metadata =
        SnapshotMetadata.manual(workspace, "before-upgrade", "user:x", Path.of("/snaps/t"));
    assertEquals(workspace, metadata.workspaceId());
    assertEquals("before-upgrade", metadata.tag());
    assertEquals(SnapshotKind.MANUAL, metadata.kind());
    assertEquals(Optional.empty(), metadata.parentId());
  }

  @Test
  void blankFieldsAndNegativeSizesAreRejected() {
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    Path path = Path.of("/snaps/t");
    assertThrows(
        IllegalArgumentException.class,
        () -> SnapshotMetadata.manual(workspace, "  ", "user:x", path));
    assertThrows(
        IllegalArgumentException.class, () -> SnapshotMetadata.manual(workspace, "t", "  ", path));
    assertThrows(NullPointerException.class, () -> SnapshotMetadata.manual(null, "t", "u", path));
    SnapshotMetadata metadata = SnapshotMetadata.manual(workspace, "t", "u", path);
    assertTrue(metadata.logicalBytes() == 0 && metadata.physicalBytes() == 0);
  }
}
