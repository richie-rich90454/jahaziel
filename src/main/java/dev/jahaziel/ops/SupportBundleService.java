/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/**
 * Builds a sanitized support archive under {@code data/support}. Configuration, rotated logs, a
 * health snapshot and an audit tail are collected, scrubbed of secrets, and covered by a SHA-256
 * manifest stored inside the archive.
 */
public final class SupportBundleService {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final Path dataDir;
    private final Clock clock;
    private final Path supportDir;

    /** Creates a bundle builder using the system clock. */
    public SupportBundleService(Path dataDir) {
        this(dataDir, Clock.systemUTC());
    }

    /** Creates a bundle builder with an explicit clock. */
    public SupportBundleService(Path dataDir, Clock clock) {
        if (dataDir == null) {
            throw new IllegalArgumentException("dataDir must not be null");
        }
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.clock = clock;
        this.supportDir = this.dataDir.resolve("support");
        OpsFiles.ensureDir(this.supportDir);
    }

    /** Returns the support directory. */
    public Path supportDir() {
        return supportDir;
    }

    /**
     * Creates a support bundle.
     *
     * @param config configuration key/value pairs, sanitized before export
     * @param health health metrics, sanitized before export
     * @param auditTail recent audit lines, sanitized before export
     * @return the archive under data/support
     */
    public Path createBundle(
            Map<String, String> config, Map<String, String> health, List<String> auditTail) {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put(
                "config.txt",
                OpsFiles.sanitizedMap(config == null ? new LinkedHashMap<String, String>() : config)
                        .getBytes(StandardCharsets.UTF_8));
        entries.put("health.json", healthJson(health).getBytes(StandardCharsets.UTF_8));
        entries.put("audit-tail.txt", auditText(auditTail).getBytes(StandardCharsets.UTF_8));
        addLogs(entries);

        Map<String, String> hashes = new LinkedHashMap<String, String>();
        List<String> names = new ArrayList<String>(entries.keySet());
        OpsFiles.sortStrings(names);
        for (int i = 0; i < names.size(); i++) {
            hashes.put(names.get(i), OpsFiles.sha256(entries.get(names.get(i))));
        }
        StringBuilder manifest = new StringBuilder("# sha256 manifest v1\n");
        for (int i = 0; i < names.size(); i++) {
            manifest.append(hashes.get(names.get(i)))
                    .append("  ")
                    .append(names.get(i))
                    .append('\n');
        }
        entries.put("manifest.sha256", manifest.toString().getBytes(StandardCharsets.UTF_8));

        String stamp = Instant.now(clock).atZone(ZoneId.of("UTC")).format(STAMP);
        Path zip = supportDir.resolve("support-" + stamp + ".zip");
        try {
            OutputStream fileOut = Files.newOutputStream(zip);
            ZipOutputStream zipOut = new ZipOutputStream(fileOut);
            try {
                List<String> ordered = new ArrayList<String>(entries.keySet());
                OpsFiles.sortStrings(ordered);
                for (int i = 0; i < ordered.size(); i++) {
                    String name = ordered.get(i);
                    ZipUtil.put(zipOut, name, entries.get(name));
                }
            } finally {
                zipOut.close();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot write support bundle", failure);
        }
        return zip;
    }

    /** Returns true when the bundle's manifest matches its contents. */
    public boolean verify(Path bundle) {
        Map<String, byte[]> entries = ZipUtil.readEntries(bundle);
        byte[] manifestBytes = entries.remove("manifest.sha256");
        if (manifestBytes == null) {
            return false;
        }
        Path temp = supportDir.resolve(".manifest-check");
        OpsFiles.writeText(temp, new String(manifestBytes, StandardCharsets.UTF_8));
        boolean ok = ChecksumManifest.verifyEntries(entries, temp);
        OpsFiles.deleteIfPresent(temp);
        return ok;
    }

    private void addLogs(Map<String, byte[]> entries) {
        Path logs = dataDir.resolve("logs");
        List<Path> files = OpsFiles.listFiles(logs);
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            if (!OpsFiles.contained(dataDir, file)) {
                continue;
            }
            try {
                String text =
                        OpsFiles.sanitizeText(
                                new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                entries.put(
                        "logs/" + file.getFileName().toString(),
                        text.getBytes(StandardCharsets.UTF_8));
            } catch (IOException failure) {
                throw new IllegalStateException("cannot read log file " + file, failure);
            }
        }
    }

    private static String healthJson(Map<String, String> health) {
        Map<String, String> values = health == null ? new LinkedHashMap<String, String>() : health;
        List<String> keys = OpsFiles.sortedKeys(values);
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (OpsFiles.isSecretKey(key)) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(OpsFiles.jsonEscape(key)).append("\":");
            out.append('"')
                    .append(OpsFiles.jsonEscape(OpsFiles.sanitizeText(values.get(key))))
                    .append('"');
        }
        return out.append('}').toString();
    }

    private static String auditText(List<String> auditTail) {
        StringBuilder out = new StringBuilder();
        if (auditTail == null) {
            return out.toString();
        }
        for (int i = 0; i < auditTail.size(); i++) {
            out.append(OpsFiles.sanitizeText(auditTail.get(i))).append('\n');
        }
        return out.toString();
    }
}
