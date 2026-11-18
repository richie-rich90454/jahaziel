/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.AuditEvent;
import dev.jahaziel.spi.AuditSink;
import dev.jahaziel.spi.Outcome;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Writes agent decisions to the platform's append-only audit sink. */
public final class AgentAudit {
    private final AuditSink sink;

    public AgentAudit(AuditSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Appends one successful decision event.
     *
     * @param user the acting user
     * @param action the short action name
     * @param target the target identifier
     * @param targetType the kind of target
     * @param detailKey an extra detail key
     * @param detailValue an extra detail value
     */
    public void record(
            UserId user,
            String action,
            String target,
            String targetType,
            String detailKey,
            String detailValue) {
        Objects.requireNonNull(user, "user");
        HashMap<String, String> details = new HashMap<String, String>();
        if (detailKey != null && detailValue != null) {
            details.put(detailKey, detailValue);
        }
        AuditEvent event =
            new AuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                user.value().toString(),
                ActorType.USER,
                action,
                Optional.of(target),
                Optional.of(targetType),
                Outcome.SUCCESS,
                Optional.<String>empty(),
                Optional.<String>empty(),
                details,
                Optional.<String>empty(),
                "");
        sink.write(event);
    }
}
