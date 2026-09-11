/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * A snapshot was created.
 *
 * @param workspaceId the workspace
 * @param snapshotId the snapshot
 * @param occurredAt when the event occurred
 */
public record SnapshotCreatedEvent(
    WorkspaceId workspaceId, SnapshotId snapshotId, Instant occurredAt) implements DomainEvent {
  public SnapshotCreatedEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(snapshotId, "snapshotId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
