/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavior of {@link AuditQueryService}: filters, exports and hash-chain verification. */
class AuditQueryServiceTest {
    private static List<AuditQueryService.AuditEntry> chain() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        List<AuditQueryService.AuditEntry> entries = new ArrayList<AuditQueryService.AuditEntry>();
        AuditQueryService.AuditEntry first =
                new AuditQueryService.AuditEntry("id1", t0, "alice", "login", "ws1", "SUCCESS", "ok", "");
        entries.add(first);
        entries.add(
                new AuditQueryService.AuditEntry(
                        "id2",
                        t0.plusSeconds(10),
                        "bob",
                        "snapshot.create",
                        "ws1",
                        "SUCCESS",
                        "tag, with comma",
                        first.hash()));
        AuditQueryService.AuditEntry third =
                new AuditQueryService.AuditEntry(
                        "id3", t0.plusSeconds(20), "alice", "delete", "ws2", "FAILURE", "denied", entries.get(1).hash());
        entries.add(third);
        return entries;
    }

    @Test
    void filtersByEverySupportedField() {
        AuditQueryService service = new AuditQueryService();
        List<AuditQueryService.AuditEntry> entries = chain();
        AuditQueryService.AuditFilter filter = new AuditQueryService.AuditFilter();
        filter.actor("alice");
        assertEquals(2, service.query(entries, filter).size());
        filter = new AuditQueryService.AuditFilter();
        filter.action("login");
        assertEquals(1, service.query(entries, filter).size());
        filter = new AuditQueryService.AuditFilter();
        filter.workspace("ws2");
        assertEquals(1, service.query(entries, filter).size());
        filter = new AuditQueryService.AuditFilter();
        filter.result("FAILURE");
        assertEquals(1, service.query(entries, filter).size());
        filter = new AuditQueryService.AuditFilter();
        filter.from(Instant.parse("2026-01-01T00:00:15Z"));
        assertEquals(1, service.query(entries, filter).size());
        filter = new AuditQueryService.AuditFilter();
        filter.to(Instant.parse("2026-01-01T00:00:05Z"));
        assertEquals(1, service.query(entries, filter).size());
    }

    @Test
    void exportsCsvAndJson() {
        AuditQueryService service = new AuditQueryService();
        List<AuditQueryService.AuditEntry> entries = chain();
        String csv = service.toCsv(entries);
        assertTrue(csv.startsWith("eventId,timestamp,actor"));
        assertTrue(csv.contains("alice"));
        assertTrue(csv.contains("\"tag, with comma\""));
        String json = service.toJson(entries);
        assertTrue(json.startsWith("[{"));
        assertTrue(json.contains("\"actor\":\"alice\""));
    }

    @Test
    void verifiesAndDetectsBrokenChain() {
        AuditQueryService service = new AuditQueryService();
        List<AuditQueryService.AuditEntry> entries = chain();
        AuditQueryService.ChainStatus valid = service.verifyChain(entries);
        assertTrue(valid.valid());
        assertEquals(-1, valid.brokenIndex());

        List<AuditQueryService.AuditEntry> tampered = new ArrayList<AuditQueryService.AuditEntry>(entries);
        tampered.set(
                2,
                new AuditQueryService.AuditEntry(
                        "id3",
                        Instant.parse("2026-01-01T00:00:20Z"),
                        "alice",
                        "delete",
                        "ws2",
                        "FAILURE",
                        "denied",
                        entries.get(1).hash(),
                        "deadbeef"));
        AuditQueryService.ChainStatus broken = service.verifyChain(tampered);
        assertFalse(broken.valid());
        assertEquals(2, broken.brokenIndex());
    }
}
