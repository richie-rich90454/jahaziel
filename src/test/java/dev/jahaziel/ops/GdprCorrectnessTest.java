/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** GDPR export and delete now resolve the real stores even when callers pass empty maps. */
class GdprCorrectnessTest {
    @TempDir Path temp;

    private Path data() throws Exception {
        Path data = temp.resolve("data");
        Files.createDirectories(data);
        Class.forName("org.sqlite.JDBC");
        try (Connection c =
                        DriverManager.getConnection("jdbc:sqlite:" + data.resolve("jahaziel.db"));
                Statement s = c.createStatement()) {
            s.execute(
                    "CREATE TABLE app_user (user_id TEXT PRIMARY KEY, external_id TEXT, email TEXT, display_name TEXT, status TEXT, created_at TEXT, last_login_at TEXT)");
            s.execute(
                    "CREATE TABLE user_identity (provider TEXT, subject TEXT, user_id TEXT, email TEXT, display_name TEXT, linked_at TEXT)");
            s.execute(
                    "CREATE TABLE bff_session (session_id TEXT PRIMARY KEY, user_id TEXT, created_at TEXT, expires_at TEXT)");
            s.execute("CREATE TABLE user_role (user_id TEXT, role TEXT)");
            s.execute("CREATE TABLE user_group (user_id TEXT, group_name TEXT)");
            s.execute("CREATE TABLE workspace (workspace_id TEXT PRIMARY KEY, owner_user_id TEXT)");
            s.execute(
                    "CREATE TABLE audit_event (event_id TEXT, timestamp TEXT, actor TEXT, actor_type TEXT, action TEXT, target TEXT, target_type TEXT, outcome TEXT, source_ip TEXT, details TEXT, prev_hash TEXT, hash TEXT)");
            s.execute(
                    "INSERT INTO app_user VALUES ('u-1', 'alice', 'alice@example.test', 'Alice', 'ACTIVE', '2026-01-01 00:00:00', '')");
            s.execute(
                    "INSERT INTO user_identity VALUES ('basis', 'alice', 'u-1', 'alice@example.test', 'Alice', '2026-01-01 00:00:00')");
            s.execute("INSERT INTO bff_session VALUES ('s-1', 'u-1', '', '')");
            s.execute("INSERT INTO user_role VALUES ('u-1', 'ADMIN')");
            s.execute("INSERT INTO user_group VALUES ('u-1', 'ops')");
            s.execute("INSERT INTO workspace VALUES ('w-1', 'u-1')");
            s.execute(
                    "INSERT INTO audit_event VALUES ('e-1', '2026-01-01 00:00:00', 'alice', 'USER', 'login', 'w-1', 'workspace', 'SUCCESS', '', '{}', '', 'abc')");
        }
        Path local = data.resolve("local");
        Files.createDirectories(local);
        Files.writeString(
                local.resolve("users.json"),
                "{\"alice\":{\"email\":\"alice@example.test\",\"secret\":\"x\"},\"bob\":{\"email\":\"bob@example.test\"}}",
                StandardCharsets.UTF_8);
        return data;
    }

    private static int count(Path data, String table) throws Exception {
        try (Connection c =
                        DriverManager.getConnection("jdbc:sqlite:" + data.resolve("jahaziel.db"));
                Statement s = c.createStatement();
                ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    void exportResolvesProfileAndAuditsFromDatabase() throws Exception {
        Path data = data();
        GdprService service = new GdprService(data);
        Path archive =
                service.exportUser(
                        "alice",
                        new LinkedHashMap<String, String>(),
                        new ArrayList<AuditQueryService.AuditEntry>());
        Map<String, byte[]> entries = ZipUtil.readEntries(archive);
        String profile = new String(entries.get("user.json"), StandardCharsets.UTF_8);
        assertTrue(profile.contains("alice@example.test"));
        assertTrue(profile.contains("Alice"));
        String audits = new String(entries.get("audits.csv"), StandardCharsets.UTF_8);
        assertTrue(audits.contains("login"));
    }

    @Test
    void deleteRemovesDatabaseRowsAndScrubsFileStores() throws Exception {
        Path data = data();
        GdprService service = new GdprService(data);
        String pseudonym =
                service.deleteUser(
                        "alice", "DELETE alice", new ArrayList<AuditQueryService.AuditEntry>());
        assertEquals(GdprService.pseudonym("alice"), pseudonym);
        assertEquals(0, count(data, "app_user"));
        assertEquals(0, count(data, "user_identity"));
        assertEquals(0, count(data, "bff_session"));
        assertEquals(0, count(data, "user_role"));
        assertEquals(0, count(data, "user_group"));
        // Ownership is nulled, not deleted: the workspace itself survives.
        assertEquals(1, count(data, "workspace"));
        String users =
                Files.readString(
                        data.resolve("local").resolve("users.json"), StandardCharsets.UTF_8);
        assertFalse(users.contains("alice"));
        assertTrue(users.contains("bob"));
        String retained =
                OpsFiles.readText(
                        service.gdprDir().resolve("retained").resolve(pseudonym + ".jsonl"));
        assertTrue(retained.contains(pseudonym));
        assertFalse(retained.contains("\"actor\":\"alice\""));
    }
}
