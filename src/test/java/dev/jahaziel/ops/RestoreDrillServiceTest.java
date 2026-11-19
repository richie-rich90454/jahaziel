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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

/** Behavior of {@link RestoreDrillService}: integrity, RTO/RPO reporting and zip-slip defense. */
class RestoreDrillServiceTest {
    @TempDir Path temp;

    private Path source() throws Exception {
        Path dir = temp.resolve("source");
        Files.createDirectories(dir);
        Files.write(dir.resolve("data.txt"), "payload".getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    @Test
    void drillsLatestVerifiedBackupIntoDisposableWorkspace() throws Exception {
        Path data = temp.resolve("data");
        new ScheduledBackupService(data, new TestClock("2026-01-01T01:00:00Z")).backup(source());
        RestoreDrillService service = new RestoreDrillService(data);
        RestoreDrillService.RestoreReport report = service.drill();
        assertTrue(report.success(), report.reason());
        assertEquals(1, report.files());
        assertEquals(1440L, report.rpoMinutes());
        assertEquals(120L, report.rtoMinutes());
        assertTrue(report.withinRto());
        assertEquals(0, OpsFiles.listFiles(service.drillDir()).size());
    }

    @Test
    void detectsTamperedManifest() throws Exception {
        Path data = temp.resolve("data");
        Path archive =
                new ScheduledBackupService(data, new TestClock("2026-01-01T01:00:00Z"))
                        .backup(source());
        String name = archive.getFileName().toString();
        Path manifest =
                data.resolve("backups")
                        .resolve(name.substring(0, name.length() - 4) + ".manifest.sha256");
        OpsFiles.writeText(manifest, "# sha256 manifest v1\ndeadbeef  data.txt\n");
        RestoreDrillService.RestoreReport report = new RestoreDrillService(data).drill();
        assertFalse(report.success());
        assertTrue(report.reason().contains("integrity"));
    }

    @Test
    void rejectsZipSlipEntry() throws Exception {
        final Path data = temp.resolve("data");
        Path backups = data.resolve("backups");
        Files.createDirectories(backups);
        Path archive = backups.resolve("backup-20260105-010000.zip");
        byte[] content = "escape".getBytes(StandardCharsets.UTF_8);
        OutputStream fileOut = Files.newOutputStream(archive);
        ZipOutputStream zip = new ZipOutputStream(fileOut);
        try {
            ZipUtil.put(zip, "../escape.txt", content);
        } finally {
            zip.close();
        }
        OpsFiles.writeText(
                backups.resolve("backup-20260105-010000.manifest.sha256"),
                "# sha256 manifest v1\n" + OpsFiles.sha256(content) + "  ../escape.txt\n");

        final RestoreDrillService service = new RestoreDrillService(data);
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        service.drill();
                    }
                });
        assertFalse(Files.exists(temp.resolve("escape.txt")));
    }

    @Test
    void reportsMissingBackup() {
        RestoreDrillService.RestoreReport report =
                new RestoreDrillService(temp.resolve("data")).drill();
        assertFalse(report.success());
        assertTrue(report.reason().contains("no backup"));
    }
}
