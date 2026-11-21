/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The chain digest covers every meaning-bearing field and verification fails on an empty log. */
class AuditChainTest {
    private static AuditRecord record(
            String actorType,
            String targetType,
            String sourceIp,
            Map<String, String> details,
            String hash) {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant when = Instant.parse("2026-01-01T00:00:00Z");
        String canonical =
                AuditChain.hash(
                        id.toString(),
                        when.toString(),
                        "alice",
                        actorType,
                        "login",
                        "w-1",
                        targetType,
                        Outcome.SUCCESS.name(),
                        AuditChain.detailsJson(
                                new AuditRecord(
                                        id,
                                        when,
                                        "alice",
                                        ActorType.USER,
                                        "login",
                                        Optional.of("w-1"),
                                        Optional.of(targetType),
                                        Outcome.SUCCESS,
                                        Optional.of(sourceIp),
                                        Optional.empty(),
                                        details,
                                        Optional.empty(),
                                        "")),
                        sourceIp,
                        AuditChain.GENESIS);
        return new AuditRecord(
                id,
                when,
                "alice",
                ActorType.valueOf(actorType),
                "login",
                Optional.of("w-1"),
                Optional.of(targetType),
                Outcome.SUCCESS,
                Optional.of(sourceIp),
                Optional.empty(),
                details,
                Optional.empty(),
                hash.isEmpty() ? canonical : hash);
    }

    @Test
    void emptyChainIsNeverIntact() {
        assertFalse(AuditChain.verify(new ArrayList<AuditRecord>()));
        assertFalse(AuditChain.verify(null));
    }

    @Test
    void digestCoversDetailsSourceIpAndTypes() {
        Map<String, String> details = Map.of("k", "v");
        AuditRecord original = record("USER", "workspace", "10.0.0.1", details, "");
        List<AuditRecord> chain = new ArrayList<AuditRecord>();
        chain.add(original);
        assertTrue(AuditChain.verify(chain));

        List<AuditRecord> changedDetails = new ArrayList<AuditRecord>();
        changedDetails.add(
                record("USER", "workspace", "10.0.0.1", Map.of("k", "x"), original.hash()));
        assertFalse(AuditChain.verify(changedDetails));

        List<AuditRecord> changedIp = new ArrayList<AuditRecord>();
        changedIp.add(record("USER", "workspace", "10.0.0.2", details, original.hash()));
        assertFalse(AuditChain.verify(changedIp));

        List<AuditRecord> changedType = new ArrayList<AuditRecord>();
        changedType.add(record("ADMIN", "workspace", "10.0.0.1", details, original.hash()));
        assertFalse(AuditChain.verify(changedType));

        List<AuditRecord> changedTarget = new ArrayList<AuditRecord>();
        changedTarget.add(record("USER", "document", "10.0.0.1", details, original.hash()));
        assertFalse(AuditChain.verify(changedTarget));
    }
}
