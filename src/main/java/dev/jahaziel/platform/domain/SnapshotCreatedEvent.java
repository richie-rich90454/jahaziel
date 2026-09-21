/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A snapshot was created.
 *
 * @param workspaceId the workspace
 * @param snapshotId the snapshot
 * @param occurredAt when the event occurred
 */
public record SnapshotCreatedEvent(UUID workspaceId, UUID snapshotId, Instant occurredAt)
    implements DomainEvent {
  public SnapshotCreatedEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(snapshotId, "snapshotId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
