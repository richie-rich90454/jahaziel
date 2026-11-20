/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Instant;
import java.util.Iterator;

/** Append-only audit store. Only retention deletes, never row deletes. */
@JahazielSpi(version = 1)
public abstract class AuditSink {
    /**
     * Writes one audit event.
     *
     * @param event the event to append
     */
    public abstract void write(AuditEvent event);

    /**
     * Queries stored audit events.
     *
     * @param query the query criteria
     * @return the matching events
     */
    public abstract Iterator<AuditEvent> query(AuditQuery query);

    /**
     * Deletes events before a cutoff. Retention only.
     *
     * @param cutoff events before this instant are deleted
     * @return the number of deleted events
     */
    public abstract int deleteBefore(Instant cutoff);
}
