/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.support;

import dev.jahaziel.spi.AuditEvent;
import dev.jahaziel.spi.AuditQuery;
import dev.jahaziel.spi.AuditSink;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Test double that keeps every audit event in memory. */
public final class FakeAuditSink extends AuditSink {
    private final List<AuditEvent> events = new ArrayList<AuditEvent>();

    @Override
    public void write(AuditEvent event) {
        events.add(event);
    }

    @Override
    public Iterator<AuditEvent> query(AuditQuery query) {
        return Collections.emptyIterator();
    }

    @Override
    public int deleteBefore(Instant cutoff) {
        return 0;
    }

    /** Returns a copy of the captured events. */
    public List<AuditEvent> events() {
        return new ArrayList<AuditEvent>(events);
    }
}
