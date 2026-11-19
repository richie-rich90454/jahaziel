/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link ScheduledBackupService}: archives, manifests, retention and containment. */
class ScheduledBackupServiceTest {
    @TempDir Path temp;

    private Path source(String name) throws Exception {
        Path dir = temp.resolve("source-" + name);
        Files.createDirectories(dir);
        Files.write(dir.resolve("data.txt"), name.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    @Test
    void backupIsCompressedAndManifestedInsideDataBackups() throws Exception {
        Path data = temp.resolve("data");
        ScheduledBackupService service = new ScheduledBackupService(data, new TestClock("2026-01-01T01:00:00Z"));
        Path archive = service.backup(source("one"));
        assertTrue(OpsFiles.contained(service.backupDir(), archive));
        assertTrue(Files.isRegularFile(archive));
        String name = archive.getFileName().toString();
        Path manifest = service.backupDir().resolve(name.substring(0, name.length() - 4) + ".manifest.sha256");
        assertTrue(Files.isRegularFile(manifest));
        Map<String, byte[]> entries = ZipUtil.readEntries(archive);
        assertTrue(ChecksumManifest.verifyEntries(entries, manifest));
        assertEquals("one", new String(entries.get("data.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void retentionDeletesOldestArchivesAndManifests() throws Exception {
        Path data = temp.resolve("data");
        String[] instants = {"2026-01-01T01:00:00Z", "2026-01-02T01:00:00Z", "2026-01-03T01:00:00Z"};
        for (int i = 0; i < instants.length; i++) {
            new ScheduledBackupService(data, new TestClock(instants[i])).backup(source("v" + i));
        }
        ScheduledBackupService service = new ScheduledBackupService(data);
        List<Path> before = service.listBackups();
        assertEquals(3, before.size());
        assertEquals(2, service.applyRetention(1));
        List<Path> after = service.listBackups();
        assertEquals(1, after.size());
        assertEquals(before.get(2).getFileName(), after.get(0).getFileName());
        Path removedManifest =
                service
                        .backupDir()
                        .resolve(
                                before.get(0).getFileName().toString().replace(".zip", ".manifest.sha256"));
        assertFalse(Files.exists(removedManifest));
    }

    @Test
    void objectivesAreDocumented() {
        assertEquals(1440L, ScheduledBackupService.RPO_MINUTES);
        assertEquals(120L, ScheduledBackupService.RTO_MINUTES);
    }
}
