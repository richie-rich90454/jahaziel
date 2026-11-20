/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;

/** Domain event. Security-relevant events persist to the audit store before delivery. */
public abstract class DomainEvent {
    /**
     * Returns when the event occurred.
     *
     * @return the occurrence instant
     */
    public abstract Instant occurredAt();
}
