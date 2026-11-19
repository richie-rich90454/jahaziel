/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Behavior of {@link GdprService}: sanitized exports and typed-confirmation deletion. */
class GdprServiceTest {
    @TempDir Path temp;

    private List<AuditQueryService.AuditEntry> audits() {
        List<AuditQueryService.AuditEntry> entries = new ArrayList<AuditQueryService.AuditEntry>();
        entries.add(
                new AuditQueryService.AuditEntry(
                        "ev1",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "alice",
                        "login",
                        "ws1",
                        "SUCCESS",
                        "Bearer abc.def.ghi",
                        ""));
        return entries;
    }

    @Test
    void exportIsSanitizedAndManifested() throws Exception {
        Path data = temp.resolve("data");
        Map<String, String> profile = new HashMap<String, String>();
        profile.put("email", "alice@example.test");
        profile.put("password", "hunter2");
        profile.put("api_token", "tok-123456");
        GdprService service = new GdprService(data, new TestClock("2026-01-01T00:00:00Z"));
        Path archive = service.exportUser("alice", profile, audits());
        assertTrue(OpsFiles.contained(service.gdprDir(), archive));
        Map<String, byte[]> entries = ZipUtil.readEntries(archive);
        String user = new String(entries.get("user.json"), StandardCharsets.UTF_8);
        String auditCsv = new String(entries.get("audits.csv"), StandardCharsets.UTF_8);
        assertTrue(user.contains("alice@example.test"));
        assertFalse(user.contains("hunter2"));
        assertFalse(user.contains("tok-123456"));
        assertFalse(auditCsv.contains("abc.def.ghi"));
        byte[] manifestBytes = entries.remove("manifest.sha256");
        Path manifest = temp.resolve("manifest.sha256");
        Files.write(manifest, manifestBytes);
        assertTrue(ChecksumManifest.verifyEntries(entries, manifest));
    }

    @Test
    void deleteRequiresTypedConfirmation() throws Exception {
        final GdprService service = new GdprService(temp.resolve("data"));
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        service.deleteUser("alice", "yes", audits());
                    }
                });
    }

    @Test
    void deletePseudonymizesAuditsAndRemovesUserData() throws Exception {
        Path data = temp.resolve("data");
        Path userDir = data.resolve("users").resolve("alice");
        Files.createDirectories(userDir);
        Files.write(userDir.resolve("notes.txt"), "private".getBytes(StandardCharsets.UTF_8));
        GdprService service = new GdprService(data);
        String pseudonym = service.deleteUser("alice", "DELETE alice", audits());
        assertEquals(GdprService.pseudonym("alice"), pseudonym);
        assertFalse(Files.exists(userDir));
        Path retained = service.gdprDir().resolve("retained").resolve(pseudonym + ".jsonl");
        String text = OpsFiles.readText(retained);
        assertTrue(text.contains(pseudonym));
        assertFalse(text.contains("\"actor\":\"alice\""));
        assertFalse(text.contains("abc.def.ghi"));
    }
}
