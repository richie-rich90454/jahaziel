/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.cli.CliTestSupport.Output;
import dev.jahaziel.ops.GdprService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/** Offline start flag and the ops subcommands that delegate to existing services. */
class OfflineOpsTest {
    @TempDir private Path jar;

    private String userDir;

    @BeforeEach
    void pinUserDir() throws Exception {
        userDir = System.getProperty("user.dir");
        System.setProperty("user.dir", jar.toString());
        CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
        Files.createDirectories(jar.resolve("data"));
    }

    @AfterEach
    void restoreUserDir() {
        System.setProperty("user.dir", userDir);
        Cli.setOffline(false);
    }

    @Test
    void offlineStartFlagReportsCapabilitiesUnavailable() {
        Output run = CliTestSupport.dispatch("--demo", "--offline");
        assertEquals(Cli.PASS_THROUGH, run.code());
        assertTrue(run.out().contains("offline: true (tool downloads disabled"));
        assertTrue(Cli.isOffline());
        Output capabilities = CliTestSupport.dispatch("capabilities", "--offline");
        assertEquals(0, capabilities.code());
        assertTrue(capabilities.out().contains("capability: opencode - unavailable (offline)"));
        assertTrue(capabilities.out().contains("capability: updates - unavailable (offline)"));

        Output online = CliTestSupport.dispatch("capabilities");
        assertFalse(Cli.isOffline());
        assertTrue(online.out().contains("capability: opencode - available"));
    }

    @Test
    void typedDeleteConfirmationIsEnforced() throws Exception {
        Path user = Files.createDirectories(jar.resolve("data/users/alice"));
        Files.write(user.resolve("note.txt"), "x".getBytes(StandardCharsets.UTF_8));
        writeAudit();

        Output missing = CliTestSupport.dispatch("ops", "delete-user", "alice");
        assertEquals(2, missing.code());
        assertTrue(missing.err().contains("typed confirmation required: DELETE alice"));

        Output wrong = CliTestSupport.dispatch("ops", "delete-user", "alice", "--confirm", "delete alice");
        assertEquals(2, wrong.code());
        assertTrue(wrong.err().contains("typed confirmation required"));

        assertTrue(Files.exists(user));
        Output ok =
                CliTestSupport.dispatch(
                        "ops", "delete-user", "alice", "--confirm", "DELETE alice");
        assertEquals(0, ok.code());
        assertTrue(ok.out().contains("deleted alice"));
        assertFalse(Files.exists(user));
        assertTrue(Files.isDirectory(jar.resolve("data/gdpr/retained")));

        final GdprService service = new GdprService(jar.resolve("data"));
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.deleteUser("alice", "WRONG", new ArrayList<>());
                    }
                });
    }

    @Test
    void backupAndRestoreDrillDelegateToServices() throws Exception {
        Path workspaces = Files.createDirectories(jar.resolve("data/workspaces"));
        Files.write(workspaces.resolve("readme.txt"), "hi".getBytes(StandardCharsets.UTF_8));

        Output backup = CliTestSupport.dispatch("ops", "backup");
        assertEquals(0, backup.code());
        assertTrue(backup.out().contains("snapshot: "));
        assertTrue(backup.out().contains("RPO=1440m"));
        assertTrue(Files.isDirectory(jar.resolve("data/backups")));

        Output drill = CliTestSupport.dispatch("ops", "restore-drill");
        assertEquals(0, drill.code());
        assertTrue(drill.out().contains("restore drill ok"));
    }

    @Test
    void supportBundleDelegatesToService() {
        Output output = CliTestSupport.dispatch("ops", "support-bundle");
        assertEquals(0, output.code());
        assertTrue(output.out().contains("support bundle: "));
        assertTrue(output.out().contains("verified=true"));
    }

    @Test
    void auditQueryAndExportUserDelegateToServices() throws Exception {
        writeAudit();
        Output query = CliTestSupport.dispatch("ops", "audit", "query", "--format=json");
        assertEquals(0, query.code());
        assertTrue(query.out().contains("\"eventId\":\"evt-1\""));
        assertTrue(query.out().contains("# chain="));
        Output csv = CliTestSupport.dispatch("ops", "audit", "query", "--actor=alice");
        assertEquals(0, csv.code());
        assertTrue(csv.out().contains("eventId,timestamp,actor"));

        Output exported = CliTestSupport.dispatch("ops", "export-user", "alice");
        assertEquals(0, exported.code());
        assertTrue(exported.out().contains("GDPR export: "));
        assertTrue(Files.isDirectory(jar.resolve("data/gdpr")));
    }

    @Test
    void reindexDelegatesToSearchIndex() throws Exception {
        UUID workspace = UUID.randomUUID();
        Path home = Files.createDirectories(jar.resolve("data/workspaces/" + workspace + "/home"));
        Files.write(home.resolve("note.txt"), "alpha beta".getBytes(StandardCharsets.UTF_8));

        Output output = CliTestSupport.dispatch("ops", "reindex");
        assertEquals(0, output.code());
        assertTrue(output.out().contains("reindexed " + workspace));
        assertTrue(Files.isRegularFile(jar.resolve("data/index/" + workspace + ".db")));
    }

    @Test
    void opsUsageErrors() {
        assertEquals(2, CliTestSupport.dispatch("ops").code());
        assertEquals(2, CliTestSupport.dispatch("ops", "unknown").code());
        assertEquals(2, CliTestSupport.dispatch("ops", "audit").code());
        assertEquals(2, CliTestSupport.dispatch("ops", "delete-user").code());
        Output badFormat =
                CliTestSupport.dispatch("ops", "audit", "query", "--format=xml");
        assertEquals(2, badFormat.code());
    }

    private void writeAudit() throws Exception {
        Path audit = Files.createDirectories(jar.resolve("data/audit"));
        String line =
                "{\"eventId\":\"evt-1\",\"timestamp\":\"2026-01-01T00:00:00Z\","
                        + "\"actor\":\"alice\",\"action\":\"login\",\"workspace\":\"\","
                        + "\"result\":\"success\",\"details\":\"ok\",\"prevHash\":\"\","
                        + "\"hash\":\"deadbeef\"}";
        Files.write(audit.resolve("audit.jsonl"), line.getBytes(StandardCharsets.UTF_8));
    }
}
