/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
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
    private final ObjectMapper mapper = new ObjectMapper();

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
     * Produces a sanitized archive of a user's profile and audit history. An empty profile or audit
     * list is resolved from the real stores so callers that cannot supply them still export the
     * subject's data.
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
        Map<String, String> resolved =
                profile == null || profile.isEmpty() ? lookupProfile(userId) : profile;
        entries.put("user.json", profileJson(safe, resolved).getBytes(StandardCharsets.UTF_8));
        List<AuditQueryService.AuditEntry> resolvedAudits = audits;
        if (resolvedAudits == null || resolvedAudits.isEmpty()) {
            resolvedAudits = forActor(auditQuery.readDatabase(dataDir), userId);
        }
        String csv = auditQuery.toCsv(resolvedAudits);
        entries.put("audits.csv", OpsFiles.sanitizeText(csv).getBytes(StandardCharsets.UTF_8));
        List<String> names = new ArrayList<String>(entries.keySet());
        OpsFiles.sortStrings(names);
        StringBuilder manifest = new StringBuilder("# sha256 manifest v1\n");
        for (int i = 0; i < names.size(); i++) {
            manifest.append(OpsFiles.sha256(entries.get(names.get(i))))
                    .append("  ")
                    .append(names.get(i))
                    .append('\n');
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
     * Database rows are deleted in one transaction; file-based identity stores are scrubbed of the
     * subject before the per-user directory is removed.
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
        List<AuditQueryService.AuditEntry> source = audits;
        if (source == null || source.isEmpty()) {
            source = forActor(auditQuery.readDatabase(dataDir), userId);
        }
        writeRetained(pseudonym, source);
        deleteDatabaseRows(userId);
        scrubIdentityStores(userId);
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

    private void writeRetained(String pseudonym, List<AuditQueryService.AuditEntry> source) {
        Path retainedDir = gdprDir.resolve("retained");
        OpsFiles.ensureDir(retainedDir);
        StringBuilder out = new StringBuilder();
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
    }

    private Map<String, String> lookupProfile(String userId) {
        Map<String, String> profile = new LinkedHashMap<String, String>();
        Path db = dataDir.resolve("jahaziel.db");
        if (!Files.isRegularFile(db)) {
            return profile;
        }
        String sql =
                "SELECT user_id, external_id, email, display_name, status, created_at,"
                        + " last_login_at FROM app_user WHERE external_id = ?"
                        + " OR CAST(user_id AS TEXT) = ? OR email = ?";
        try (Connection connection = connect(db);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, userId);
            statement.setString(3, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    put(profile, "userId", rows.getString("user_id"));
                    put(profile, "externalId", rows.getString("external_id"));
                    put(profile, "email", rows.getString("email"));
                    put(profile, "displayName", rows.getString("display_name"));
                    put(profile, "status", rows.getString("status"));
                    put(profile, "createdAt", readInstant(rows, "created_at"));
                    put(profile, "lastLoginAt", readInstant(rows, "last_login_at"));
                }
            }
            String identities =
                    "SELECT provider, subject, email, display_name FROM user_identity"
                            + " WHERE subject = ? OR CAST(user_id AS TEXT) = ?";
            try (PreparedStatement links = connection.prepareStatement(identities)) {
                links.setString(1, userId);
                links.setString(2, userId);
                StringBuilder joined = new StringBuilder();
                try (ResultSet rows = links.executeQuery()) {
                    while (rows.next()) {
                        if (joined.length() > 0) {
                            joined.append(';');
                        }
                        joined.append(rows.getString("provider"))
                                .append(':')
                                .append(rows.getString("subject"));
                    }
                }
                put(profile, "identities", joined.toString());
            }
        } catch (SQLException missing) {
            return profile;
        }
        return profile;
    }

    private void deleteDatabaseRows(String userId) {
        Path db = dataDir.resolve("jahaziel.db");
        if (!Files.isRegularFile(db)) {
            return;
        }
        // Dependent tables are keyed by the internal user_id; callers supply the external id.
        String byOwner =
                " IN (SELECT CAST(user_id AS TEXT) FROM app_user WHERE external_id = ?"
                        + " OR CAST(user_id AS TEXT) = ?)";
        String[][] statements = {
            {"DELETE FROM user_role WHERE CAST(user_id AS TEXT)" + byOwner, "owner"},
            {"DELETE FROM user_group WHERE CAST(user_id AS TEXT)" + byOwner, "owner"},
            {"DELETE FROM bff_session WHERE CAST(user_id AS TEXT)" + byOwner, "owner"},
            {
                "DELETE FROM user_identity WHERE CAST(user_id AS TEXT)"
                        + byOwner
                        + " OR subject = ?",
                "identity"
            },
            {
                "UPDATE workspace SET owner_user_id = NULL WHERE CAST(owner_user_id AS TEXT)"
                        + byOwner,
                "owner"
            },
            {"DELETE FROM app_user WHERE CAST(user_id AS TEXT) = ? OR external_id = ?", "owner"}
        };
        try (Connection connection = connect(db)) {
            connection.setAutoCommit(false);
            try {
                for (int i = 0; i < statements.length; i++) {
                    int params = statements[i][1].equals("identity") ? 3 : 2;
                    try (PreparedStatement statement =
                            connection.prepareStatement(statements[i][0])) {
                        for (int p = 1; p <= params; p++) {
                            statement.setString(p, userId);
                        }
                        statement.executeUpdate();
                    } catch (SQLException missingTable) {
                        // Schemas without an optional table still delete the remaining rows.
                    }
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw new IllegalStateException("cannot delete GDPR rows for " + userId, failure);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("cannot open database for GDPR deletion", failure);
        }
    }

    private void scrubIdentityStores(String userId) {
        Path[] roots = {
            dataDir.resolve("identity"), dataDir.resolve("oidc"), dataDir.resolve("local")
        };
        for (int r = 0; r < roots.length; r++) {
            List<Path> files = OpsFiles.listFiles(roots[r]);
            for (int i = 0; i < files.size(); i++) {
                Path file = files.get(i);
                if (!file.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                try {
                    JsonNode parsed = mapper.readTree(OpsFiles.readText(file));
                    if (parsed == null || !parsed.isObject()) {
                        continue;
                    }
                    ObjectNode root = (ObjectNode) parsed;
                    List<String> doomed = new ArrayList<String>();
                    Iterator<String> names = root.propertyNames().iterator();
                    while (names.hasNext()) {
                        String name = names.next();
                        if (userId.equals(name) || matchesSubject(root.get(name), userId)) {
                            doomed.add(name);
                        }
                    }
                    if (doomed.isEmpty()) {
                        continue;
                    }
                    for (int d = 0; d < doomed.size(); d++) {
                        root.remove(doomed.get(d));
                    }
                    OpsFiles.writeText(file, mapper.writeValueAsString(root));
                } catch (RuntimeException unreadable) {
                    // A store that cannot be parsed is left untouched rather than corrupted.
                }
            }
        }
    }

    private static boolean matchesSubject(JsonNode value, String userId) {
        if (value == null) {
            return false;
        }
        if (value.isObject()) {
            String[] keys = {"subject", "userId", "user_id", "username", "email"};
            for (int i = 0; i < keys.length; i++) {
                JsonNode field = value.get(keys[i]);
                if (field != null && field.isString() && userId.equals(field.asString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Connection connect(Path db) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // JDBC 4 drivers register through the service loader.
        }
        return DriverManager.getConnection("jdbc:sqlite:" + db);
    }

    private static String readInstant(ResultSet rows, String column) {
        try {
            Timestamp value = rows.getTimestamp(column);
            return value == null ? "" : value.toInstant().toString();
        } catch (SQLException missing) {
            return "";
        }
    }

    private static void put(Map<String, String> profile, String key, String value) {
        if (value != null && !value.isEmpty()) {
            profile.put(key, value);
        }
    }

    private static List<AuditQueryService.AuditEntry> forActor(
            List<AuditQueryService.AuditEntry> entries, String actor) {
        List<AuditQueryService.AuditEntry> out = new ArrayList<AuditQueryService.AuditEntry>();
        for (int i = 0; i < entries.size(); i++) {
            if (actor.equals(entries.get(i).actor())) {
                out.add(entries.get(i));
            }
        }
        return out;
    }

    private static String profileJson(String userId, Map<String, String> profile) {
        Map<String, String> values = profile;
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
