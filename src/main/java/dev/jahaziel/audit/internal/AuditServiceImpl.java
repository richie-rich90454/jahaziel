/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditChain;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.audit.persistence.AuditEventEntity;
import dev.jahaziel.audit.persistence.AuditEventRepository;

import org.springframework.data.domain.PageRequest;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Database-backed audit log with a hash chain. The canonical fields and digest live in {@link
 * AuditChain} so writers and verifiers agree on the same contract.
 */
public final class AuditServiceImpl extends AuditService {
    private final AuditEventRepository events;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates the service.
     *
     * @param events the event rows
     */
    public AuditServiceImpl(AuditEventRepository events) {
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public synchronized AuditRecord append(AuditRecord event) {
        Objects.requireNonNull(event, "event");
        List<AuditEventEntity> previous = events.findLatest(PageRequest.of(0, 1));
        String prevHash = "genesis";
        if (!previous.isEmpty()) {
            prevHash = previous.get(0).getHash();
        }
        Optional<String> prevLink = previous.isEmpty() ? Optional.empty() : Optional.of(prevHash);
        String hash = chainHash(prevHash, event);
        AuditRecord stored =
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
                        prevLink,
                        hash);
        events.save(AuditMapper.toEntity(stored, mapper));
        return stored;
    }

    @Override
    public List<AuditRecord> latest(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive, was " + limit);
        }
        List<AuditRecord> result = new ArrayList<>();
        for (AuditEventEntity row : events.findLatest(PageRequest.of(0, limit))) {
            result.add(AuditMapper.toDomain(row, mapper));
        }
        return result;
    }

    /**
     * Re-derives every stored hash from its fields and links. Empty logs are never reported intact.
     *
     * @param limit the maximum number of newest events to verify
     * @return true only when the chain is consistent
     */
    @Override
    public boolean verifyChain(int limit) {
        return AuditChain.verify(latest(limit));
    }

    private static String chainHash(String prevHash, AuditRecord event) {
        return AuditChain.hash(
                event.eventId().toString(),
                event.timestamp().toString(),
                event.actor(),
                event.actorType().name(),
                event.action(),
                event.target().orElse(""),
                event.targetType().orElse(""),
                event.outcome().name(),
                AuditChain.detailsJson(event),
                event.sourceIp().orElse(""),
                prevHash);
    }
}
