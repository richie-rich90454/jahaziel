/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scheduled compressed snapshots of a source tree. Archives and their manifests live only under
 * {@code data/backups}.
 *
 * <p>Recovery objectives: RPO is 24 hours (one automatic backup per day); RTO is 2 hours (the time
 * budget to restore the newest verified archive into a fresh workspace).
 */
public final class ScheduledBackupService {
    /** Recovery point objective: data loss is bounded to one day. */
    public static final Duration RPO = Duration.ofHours(24);

    /** Recovery time objective: a restore drill must complete within two hours. */
    public static final Duration RTO = Duration.ofHours(2);

    /** Reported RPO in minutes. */
    public static final long RPO_MINUTES = RPO.toMinutes();

    /** Reported RTO in minutes. */
    public static final long RTO_MINUTES = RTO.toMinutes();

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final Path dataDir;
    private final Clock clock;
    private final Path backupDir;

    /** Creates a backup service using the system clock. */
    public ScheduledBackupService(Path dataDir) {
        this(dataDir, Clock.systemUTC());
    }

    /** Creates a backup service with an explicit clock. */
    public ScheduledBackupService(Path dataDir, Clock clock) {
        if (dataDir == null) {
            throw new IllegalArgumentException("dataDir must not be null");
        }
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.clock = clock;
        this.backupDir = this.dataDir.resolve("backups");
        OpsFiles.ensureDir(this.backupDir);
    }

    /** Returns the backups directory. */
    public Path backupDir() {
        return backupDir;
    }

    /**
     * Compresses a source tree into a new archive and writes its SHA-256 manifest beside it.
     *
     * @param sourceDir the tree to back up
     * @return the archive under data/backups
     */
    public Path backup(Path sourceDir) {
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("sourceDir must be an existing directory");
        }
        String stamp = Instant.now(clock).atZone(ZoneId.of("UTC")).format(STAMP);
        Path archive = containedTarget("backup-" + stamp + ".zip");
        Map<String, String> hashes = ZipUtil.zipTree(sourceDir, archive);
        Path manifest = containedTarget("backup-" + stamp + ".manifest.sha256");
        ChecksumManifest.writeEntries(hashes, manifest);
        return archive;
    }

    /** Returns all archives under data/backups, oldest first. */
    public List<Path> listBackups() {
        List<Path> result = new ArrayList<Path>();
        List<Path> files = OpsFiles.listFiles(backupDir);
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            if (file.getFileName().toString().endsWith(".zip") && !isDrill(file)) {
                result.add(file);
            }
        }
        return result;
    }

    /** Deletes archives beyond the newest {@code keep}, with their manifests. */
    public int applyRetention(int keep) {
        if (keep < 1) {
            throw new IllegalArgumentException("keep must be at least 1");
        }
        List<Path> backups = listBackups();
        int removed = 0;
        for (int i = 0; i < backups.size() - keep; i++) {
            Path archive = backups.get(i);
            Path manifest = backupDir.resolve(stripZip(archive) + ".manifest.sha256");
            if (OpsFiles.contained(backupDir, archive)) {
                OpsFiles.deleteIfPresent(archive);
            }
            if (OpsFiles.contained(backupDir, manifest)) {
                OpsFiles.deleteIfPresent(manifest);
            }
            removed++;
        }
        return removed;
    }

    /** Returns the newest archive, or null when none exist. */
    public Path latest() {
        List<Path> backups = listBackups();
        if (backups.isEmpty()) {
            return null;
        }
        return backups.get(backups.size() - 1);
    }

    private Path containedTarget(String name) {
        Path target = backupDir.resolve(name).normalize();
        if (!OpsFiles.contained(backupDir, target)) {
            throw new IllegalArgumentException("backup destination escapes data/backups: " + name);
        }
        return target;
    }

    private static boolean isDrill(Path file) {
        return file.getFileName().toString().startsWith("drill-");
    }

    private static String stripZip(Path archive) {
        String name = archive.getFileName().toString();
        return name.substring(0, name.length() - 4);
    }
}
