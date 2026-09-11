/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;

/** Domain event. Security-relevant events persist to the audit store before delivery. */
public sealed interface DomainEvent
    permits WorkspaceCreatedEvent,
        WorkspaceDeletedEvent,
        RuntimeStartedEvent,
        RuntimeStoppedEvent,
        SnapshotCreatedEvent,
        PolicyChangedEvent,
        TerminalSessionStartedEvent,
        SecurityViolationEvent,
        NodeRegisteredEvent,
        NodeLostEvent {
  /**
   * Returns when the event occurred.
   *
   * @return the occurrence instant
   */
  Instant occurredAt();
}
