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
import java.util.List;
import java.util.stream.Stream;

/** Test double that keeps every audit event in memory. */
public final class FakeAuditSink implements AuditSink {
    private final List<AuditEvent> events = new ArrayList<AuditEvent>();

    @Override
    public void write(AuditEvent event) {
        events.add(event);
    }

    @Override
    public Stream<AuditEvent> query(AuditQuery query) {
        return Stream.empty();
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
