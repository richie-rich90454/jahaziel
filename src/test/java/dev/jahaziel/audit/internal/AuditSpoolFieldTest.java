/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Spool lines carry every audit field, deterministic ids and line-only diagnostics. */
class AuditSpoolFieldTest {
    @TempDir Path root;

    private static final String LINE =
            "{\"action\":\"file.write\",\"actor\":\"guest-7\",\"actorType\":\"NODE\","
                    + "\"target\":\"doc-1\",\"targetType\":\"document\",\"outcome\":\"DENIED\","
                    + "\"sourceIp\":\"10.1.2.3\",\"sourceNode\":\"node-a\","
                    + "\"timestamp\":\"2026-02-03T04:05:06Z\",\"details\":{\"path\":\"/a.txt\"}}";

    @Test
    void carriesAllFields() throws Exception {
        AuditService audit = mock(AuditService.class);
        Path spool = root.resolve("ws.jsonl");
        Files.writeString(spool, LINE + "\n");
        UUID workspace = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        new AuditSpoolReader(audit).drain(workspace, spool);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).append(captor.capture());
        AuditRecord record = captor.getValue();
        assertEquals("file.write", record.action());
        assertEquals("guest-7", record.actor());
        assertEquals(ActorType.NODE, record.actorType());
        assertEquals("doc-1", record.target().orElse(""));
        assertEquals("document", record.targetType().orElse(""));
        assertEquals(Outcome.DENIED, record.outcome());
        assertEquals("10.1.2.3", record.sourceIp().orElse(""));
        assertEquals("node-a", record.sourceNode().orElse(""));
        assertEquals("/a.txt", record.details().get("path"));
        assertEquals("2026-02-03T04:05:06Z", record.timestamp().toString());
    }

    @Test
    void eventIdsAreDeterministicAcrossRetries() throws Exception {
        UUID workspace = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        AuditService first = mock(AuditService.class);
        Path one = root.resolve("one.jsonl");
        Files.writeString(one, LINE + "\n");
        new AuditSpoolReader(first).drain(workspace, one);

        AuditService second = mock(AuditService.class);
        Path two = root.resolve("two.jsonl");
        Files.writeString(two, LINE + "\n");
        new AuditSpoolReader(second).drain(workspace, two);

        ArgumentCaptor<AuditRecord> a = ArgumentCaptor.forClass(AuditRecord.class);
        ArgumentCaptor<AuditRecord> b = ArgumentCaptor.forClass(AuditRecord.class);
        verify(first).append(a.capture());
        verify(second).append(b.capture());
        assertEquals(a.getValue().eventId(), b.getValue().eventId());
    }

    @Test
    void errorMentionsLineNumberButNotRawContent() throws Exception {
        AuditService audit = mock(AuditService.class);
        Path spool = root.resolve("bad.jsonl");
        String secret = "{\"action\":\"login\",\"token\":\"SUPER_SECRET_VALUE\"";
        Files.writeString(spool, "{\"action\":\"login\"}\n" + secret + "\n");
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AuditSpoolReader(audit).drain(UUID.randomUUID(), spool));
        assertTrue(failure.getMessage().contains("line 2"));
        assertFalse(failure.getMessage().contains("SUPER_SECRET_VALUE"));
        verify(audit, times(0)).append(any());
    }
}
