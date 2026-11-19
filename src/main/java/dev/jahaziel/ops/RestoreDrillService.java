/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Restores the newest backup into a disposable workspace, verifies its checksum manifest, and
 * reports the achieved RTO/RPO against the {@link ScheduledBackupService} objectives.
 */
public final class RestoreDrillService {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    /** Outcome of one restore drill. */
    public static final class RestoreReport {
        private final boolean success;
        private final String backup;
        private final int files;
        private final long elapsedMillis;
        private final String reason;

        /** Creates a report. */
        public RestoreReport(
                boolean success, String backup, int files, long elapsedMillis, String reason) {
            this.success = success;
            this.backup = backup;
            this.files = files;
            this.elapsedMillis = elapsedMillis;
            this.reason = reason;
        }

        /** Returns whether the drill succeeded. */
        public boolean success() {
            return success;
        }

        /** Returns the archive name, or empty. */
        public String backup() {
            return backup;
        }

        /** Returns the number of restored files. */
        public int files() {
            return files;
        }

        /** Returns the elapsed wall-clock milliseconds. */
        public long elapsedMillis() {
            return elapsedMillis;
        }

        /** Returns a human-readable reason. */
        public String reason() {
            return reason;
        }

        /** Returns the target RPO in minutes. */
        public long rpoMinutes() {
            return ScheduledBackupService.RPO_MINUTES;
        }

        /** Returns the target RTO in minutes. */
        public long rtoMinutes() {
            return ScheduledBackupService.RTO_MINUTES;
        }

        /** Returns whether the drill finished inside the RTO. */
        public boolean withinRto() {
            return elapsedMillis <= ScheduledBackupService.RTO.toMillis();
        }
    }

    private final Path dataDir;
    private final Clock clock;
    private final Path backupDir;
    private final Path drillDir;

    /** Creates a drill service using the system clock. */
    public RestoreDrillService(Path dataDir) {
        this(dataDir, Clock.systemUTC());
    }

    /** Creates a drill service with an explicit clock. */
    public RestoreDrillService(Path dataDir, Clock clock) {
        if (dataDir == null) {
            throw new IllegalArgumentException("dataDir must not be null");
        }
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.clock = clock;
        this.backupDir = this.dataDir.resolve("backups");
        this.drillDir = this.dataDir.resolve("restore-drills");
        OpsFiles.ensureDir(this.drillDir);
    }

    /** Returns the disposable drill workspace parent. */
    public Path drillDir() {
        return drillDir;
    }

    /** Runs the drill against the newest verified archive. */
    public RestoreReport drill() {
        long started = System.currentTimeMillis();
        ScheduledBackupService backups = new ScheduledBackupService(dataDir, clock);
        Path latest = backups.latest();
        if (latest == null) {
            return new RestoreReport(
                    false, "", 0, System.currentTimeMillis() - started, "no backup found");
        }
        String name = latest.getFileName().toString();
        Path manifest =
                backupDir.resolve(name.substring(0, name.length() - 4) + ".manifest.sha256");
        Map<String, byte[]> entries = ZipUtil.readEntries(latest);
        if (!OpsFiles.contained(backupDir, manifest)
                || !ChecksumManifest.verifyEntries(entries, manifest)) {
            return new RestoreReport(
                    false, name, 0, System.currentTimeMillis() - started, "integrity check failed");
        }
        String stamp = Instant.now(clock).atZone(ZoneId.of("UTC")).format(STAMP);
        Path target = drillDir.resolve("drill-" + stamp);
        if (!OpsFiles.contained(drillDir, target)) {
            throw new IllegalArgumentException("drill destination escapes data/restore-drills");
        }
        try {
            List<String> extracted = ZipUtil.unzip(latest, target);
            return new RestoreReport(
                    true,
                    name,
                    extracted.size(),
                    System.currentTimeMillis() - started,
                    "restored and verified");
        } finally {
            OpsFiles.deleteTree(drillDir, target);
        }
    }
}
