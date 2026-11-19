/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

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
 * GDPR data-subject operations. Exports are sanitized archives; deletion requires a typed
 * confirmation and keeps only pseudonymized audit records for accountability.
 */
public final class GdprService {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final Path dataDir;
    private final Clock clock;
    private final Path gdprDir;
    private final AuditQueryService auditQuery = new AuditQueryService();

    /** Creates a GDPR service using the system clock. */
    public GdprService(Path dataDir) {
        this(dataDir, Clock.systemUTC());
    }

    /** Creates a GDPR service with an explicit clock. */
    public GdprService(Path dataDir, Clock clock) {
        if (dataDir == null) {
            throw new IllegalArgumentException("dataDir must not be null");
        }
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.clock = clock;
        this.gdprDir = this.dataDir.resolve("gdpr");
        OpsFiles.ensureDir(this.gdprDir);
    }

    /** Returns the GDPR working directory. */
    public Path gdprDir() {
        return gdprDir;
    }

    /**
     * Produces a sanitized archive of a user's profile and audit history.
     *
     * @param userId the data subject
     * @param profile profile key/value pairs; secret keys are dropped
     * @param audits the user's audit entries
     * @return the export archive under data/gdpr
     */
    public Path exportUser(
            String userId, Map<String, String> profile, List<AuditQueryService.AuditEntry> audits) {
        String safe = safeId(userId);
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("user.json", profileJson(safe, profile).getBytes(StandardCharsets.UTF_8));
        String csv =
                auditQuery.toCsv(
                        audits == null ? new ArrayList<AuditQueryService.AuditEntry>() : audits);
        entries.put("audits.csv", OpsFiles.sanitizeText(csv).getBytes(StandardCharsets.UTF_8));
        Map<String, String> hashes = new LinkedHashMap<String, String>();
        List<String> names = new ArrayList<String>(entries.keySet());
        OpsFiles.sortStrings(names);
        StringBuilder manifest = new StringBuilder("# sha256 manifest v1\n");
        for (int i = 0; i < names.size(); i++) {
            String hash = OpsFiles.sha256(entries.get(names.get(i)));
            hashes.put(names.get(i), hash);
            manifest.append(hash).append("  ").append(names.get(i)).append('\n');
        }
        entries.put("manifest.sha256", manifest.toString().getBytes(StandardCharsets.UTF_8));

        String stamp = Instant.now(clock).atZone(ZoneId.of("UTC")).format(STAMP);
        Path archive = gdprDir.resolve("export-" + safe + "-" + stamp + ".zip");
        if (!OpsFiles.contained(gdprDir, archive)) {
            throw new IllegalArgumentException("export destination escapes data/gdpr");
        }
        try {
            java.io.OutputStream fileOut = Files.newOutputStream(archive);
            ZipOutputStream zip = new ZipOutputStream(fileOut);
            try {
                List<String> ordered = new ArrayList<String>(entries.keySet());
                OpsFiles.sortStrings(ordered);
                for (int i = 0; i < ordered.size(); i++) {
                    ZipUtil.put(zip, ordered.get(i), entries.get(ordered.get(i)));
                }
            } finally {
                zip.close();
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("cannot write GDPR export", failure);
        }
        return archive;
    }

    /**
     * Deletes a user's data after a typed confirmation, retaining pseudonymized audit records.
     *
     * @param userId the data subject
     * @param confirmation must equal {@code DELETE <userId>}
     * @param audits audit entries to pseudonymize before retention
     * @return the pseudonym used in retained records
     */
    public String deleteUser(
            String userId, String confirmation, List<AuditQueryService.AuditEntry> audits) {
        String expected = "DELETE " + userId;
        if (confirmation == null || !confirmation.equals(expected)) {
            throw new IllegalArgumentException("typed confirmation required: " + expected);
        }
        String safe = safeId(userId);
        String pseudonym = pseudonym(userId);
        Path retainedDir = gdprDir.resolve("retained");
        OpsFiles.ensureDir(retainedDir);
        StringBuilder out = new StringBuilder();
        List<AuditQueryService.AuditEntry> source =
                audits == null ? new ArrayList<AuditQueryService.AuditEntry>() : audits;
        for (int i = 0; i < source.size(); i++) {
            AuditQueryService.AuditEntry entry = source.get(i);
            out.append("{\"eventId\":\"").append(OpsFiles.jsonEscape(entry.eventId())).append('"');
            out.append(",\"actor\":\"").append(pseudonym).append('"');
            out.append(",\"action\":\"").append(OpsFiles.jsonEscape(entry.action())).append('"');
            out.append(",\"details\":\"")
                    .append(OpsFiles.jsonEscape(OpsFiles.sanitizeText(entry.details())))
                    .append("\"}\n");
        }
        Path retained = retainedDir.resolve(pseudonym + ".jsonl");
        if (!OpsFiles.contained(retainedDir, retained)) {
            throw new IllegalArgumentException("retained path escapes data/gdpr/retained");
        }
        OpsFiles.writeText(retained, out.toString());
        Path userDir = dataDir.resolve("users").resolve(safe);
        if (OpsFiles.contained(dataDir, userDir)) {
            OpsFiles.deleteTree(dataDir, userDir);
        }
        return pseudonym;
    }

    /** Returns the stable pseudonym for a user id. */
    public static String pseudonym(String userId) {
        return "anon-" + OpsFiles.sha256(userId.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private static String profileJson(String userId, Map<String, String> profile) {
        Map<String, String> values =
                profile == null ? new LinkedHashMap<String, String>() : profile;
        List<String> keys = OpsFiles.sortedKeys(values);
        StringBuilder out =
                new StringBuilder("{\"userId\":\"").append(OpsFiles.jsonEscape(userId)).append('"');
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (OpsFiles.isSecretKey(key)) {
                continue;
            }
            out.append(",\"").append(OpsFiles.jsonEscape(key)).append("\":\"");
            out.append(OpsFiles.jsonEscape(OpsFiles.sanitizeText(values.get(key)))).append('"');
        }
        return out.append('}').toString();
    }

    private static String safeId(String userId) {
        if (userId == null || !userId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("userId must be a simple identifier");
        }
        return userId;
    }
}
