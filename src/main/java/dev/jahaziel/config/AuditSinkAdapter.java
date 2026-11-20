/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.spi.AuditEvent;
import dev.jahaziel.spi.AuditQuery;
import dev.jahaziel.spi.AuditSink;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Bridges the plugin audit sink onto the platform's hash-chained append-only audit log. */
public final class AuditSinkAdapter extends AuditSink {
    private final AuditService audit;

    public AuditSinkAdapter(AuditService audit) {
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public void write(AuditEvent event) {
        audit.append(
                new AuditRecord(
                        event.eventId(),
                        event.timestamp(),
                        event.actor(),
                        event.actorType(),
                        event.action(),
                        event.target(),
                        event.targetType(),
                        event.outcome(),
                        event.sourceIp(),
                        event.sourceNode(),
                        event.details(),
                        event.prevHash(),
                        event.hash()));
    }

    @Override
    public Iterator<AuditEvent> query(AuditQuery query) {
        List<AuditEvent> result = new ArrayList<AuditEvent>();
        List<AuditRecord> records = audit.latest(query.limit());
        for (int index = 0; index < records.size(); index++) {
            AuditRecord record = records.get(index);
            if (query.from().isPresent() && record.timestamp().isBefore(query.from().get())) {
                continue;
            }
            if (query.to().isPresent() && record.timestamp().isAfter(query.to().get())) {
                continue;
            }
            if (query.actor().isPresent() && !query.actor().get().equals(record.actor())) {
                continue;
            }
            if (query.action().isPresent() && !query.action().get().equals(record.action())) {
                continue;
            }
            result.add(
                    new AuditEvent(
                            record.eventId(),
                            record.timestamp(),
                            record.actor(),
                            record.actorType(),
                            record.action(),
                            record.target(),
                            record.targetType(),
                            record.outcome(),
                            record.sourceIp(),
                            record.sourceNode(),
                            record.details(),
                            record.prevHash(),
                            record.hash()));
        }
        return result.iterator();
    }

    @Override
    public int deleteBefore(Instant cutoff) {
        // Retention is owned by the scheduled AuditRetentionJob; this sink never deletes rows.
        return 0;
    }
}
