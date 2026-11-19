/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checksum verification, staged updates and rollback, all inside the jar data directory. */
class UpdateServiceTest {
    @TempDir private Path root;

    private static PrintStream capture(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }

    private Path sums(Path jar, String hash, String name) throws Exception {
        Path file = jar.resolveSibling("SHA256SUMS");
        Files.write(
                file, (hash + "  " + name + "\n").getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void checksumVerification() throws Exception {
        Path candidate = root.resolve("candidate.jar");
        CliTestSupport.makeJar(candidate, "2.0.0");
        String digest = Cli.sha256(candidate);
        Path good = sums(candidate, digest, "candidate.jar");
        UpdateService service = new UpdateService(root, root.resolve("data"));
        assertTrue(service.verify(candidate, good));

        Path named = sums(candidate, digest, UpdateService.JAR_NAME);
        assertTrue(service.verify(candidate, named));

        Path bad =
                sums(
                        candidate,
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        "candidate.jar");
        assertFalse(service.verify(candidate, bad));
        assertFalse(service.verify(candidate, root.resolve("absent.sums")));
        assertFalse(service.verify(root.resolve("absent.jar"), good));
        assertFalse(service.verify(candidate, null));
    }

    @Test
    void stageNeverTouchesRunningJar() throws Exception {
        Path jar = Files.createDirectories(root.resolve("jar"));
        Path data = jar.resolve("data");
        Path live = jar.resolve(UpdateService.JAR_NAME);
        CliTestSupport.makeJar(live, "1.0.0");
        byte[] before = Files.readAllBytes(live);

        Path candidate = root.resolve("candidate.jar");
        CliTestSupport.makeJar(candidate, "2.0.0");
        Path sums = sums(candidate, Cli.sha256(candidate), "candidate.jar");
        UpdateService service = new UpdateService(jar, data);
        UpdateService.StageResult result = service.stage(candidate, sums, null, false);

        assertTrue(result.ok());
        assertEquals("2.0.0", result.version());
        assertEquals("2.0.0", service.pendingVersion());
        assertTrue(
                Files.isRegularFile(
                        data.resolve(UpdateService.UPDATE_DIR)
                                .resolve("2.0.0")
                                .resolve(UpdateService.JAR_NAME)));
        assertTrue(
                Files.isRegularFile(
                        data.resolve(UpdateService.UPDATE_DIR).resolve("2.0.0").resolve(UpdateService.SUMS)));
        assertEquals(java.util.Arrays.toString(before), java.util.Arrays.toString(Files.readAllBytes(live)));
        assertEquals("1.0.0", Cli.jarVersion(live));
    }

    @Test
    void swapOnRestartThenRollback() throws Exception {
        Path jar = Files.createDirectories(root.resolve("jar"));
        Path data = jar.resolve("data");
        Path live = jar.resolve(UpdateService.JAR_NAME);
        CliTestSupport.makeJar(live, "1.0.0");

        Path candidate = root.resolve("candidate.jar");
        CliTestSupport.makeJar(candidate, "2.0.0");
        Path sums = sums(candidate, Cli.sha256(candidate), "candidate.jar");
        UpdateService service = new UpdateService(jar, data);
        assertEquals(0, service.stage(candidate, sums, null, false).message().indexOf("staged "));

        ByteArrayOutputStream applied = new ByteArrayOutputStream();
        assertTrue(service.applyPending(capture(applied)));
        assertTrue(applied.toString(StandardCharsets.UTF_8).contains("applied staged update 2.0.0"));
        assertEquals("2.0.0", Cli.jarVersion(live));
        assertNull(service.pendingVersion());
        assertEquals(
                "1.0.0",
                new String(
                        Files.readAllBytes(
                                data.resolve(UpdateService.UPDATE_DIR).resolve(UpdateService.APPLIED)),
                        StandardCharsets.UTF_8));
        assertEquals(
                "1.0.0",
                Cli.jarVersion(
                        data.resolve(UpdateService.UPDATE_DIR)
                                .resolve(UpdateService.ROLLBACK)
                                .resolve("1.0.0")
                                .resolve(UpdateService.JAR_NAME)));

        ByteArrayOutputStream rolled = new ByteArrayOutputStream();
        assertTrue(service.rollback(capture(rolled)));
        assertTrue(rolled.toString(StandardCharsets.UTF_8).contains("rolled back to 1.0.0"));
        assertEquals("1.0.0", Cli.jarVersion(live));
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        assertFalse(service.rollback(capture(again)));
        assertTrue(again.toString(StandardCharsets.UTF_8).contains("no applied update"));
    }

    @Test
    void dryRunWritesNothing() throws Exception {
        Path jar = Files.createDirectories(root.resolve("jar"));
        Path data = jar.resolve("data");
        Path candidate = root.resolve("candidate.jar");
        CliTestSupport.makeJar(candidate, "3.0.0");
        Path sums = sums(candidate, Cli.sha256(candidate), "candidate.jar");
        UpdateService service = new UpdateService(jar, data);

        UpdateService.StageResult result = service.stage(candidate, sums, null, true);
        assertTrue(result.ok());
        assertTrue(result.message().startsWith("dry-run:"));
        assertFalse(Files.exists(data.resolve(UpdateService.UPDATE_DIR)));
        assertNull(service.pendingVersion());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertFalse(service.applyPending(capture(out)));
        assertEquals("", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void badChecksumRefusesStage() throws Exception {
        Path jar = Files.createDirectories(root.resolve("jar"));
        Path data = jar.resolve("data");
        Path candidate = root.resolve("candidate.jar");
        CliTestSupport.makeJar(candidate, "4.0.0");
        Path sums =
                sums(
                        candidate,
                        "1111111111111111111111111111111111111111111111111111111111111111",
                        "candidate.jar");
        UpdateService service = new UpdateService(jar, data);

        UpdateService.StageResult result = service.stage(candidate, sums, null, false);
        assertFalse(result.ok());
        assertTrue(result.message().contains("checksum verification failed"));
        assertNull(service.pendingVersion());
        assertFalse(
                Files.exists(
                        data.resolve(UpdateService.UPDATE_DIR)
                                .resolve("4.0.0")
                                .resolve(UpdateService.JAR_NAME)));
    }

    @Test
    void generatedSumsWhenNoneSupplied() throws Exception {
        Path jar = Files.createDirectories(root.resolve("jar"));
        Path data = jar.resolve("data");
        Path candidate = root.resolve("candidate.jar");
        CliTestSupport.makeJar(candidate, "5.0.0");
        UpdateService service = new UpdateService(jar, data);

        assertTrue(service.stage(candidate, null, null, false).ok());
        Path stage = data.resolve(UpdateService.UPDATE_DIR).resolve("5.0.0");
        assertTrue(service.verify(stage.resolve(UpdateService.JAR_NAME), stage.resolve(UpdateService.SUMS)));
    }

    @Test
    void statusReportsPending() throws Exception {
        Path jar = Files.createDirectories(root.resolve("jar"));
        Path data = jar.resolve("data");
        Path candidate = root.resolve("candidate.jar");
        CliTestSupport.makeJar(candidate, "6.0.0");
        Path sums = sums(candidate, Cli.sha256(candidate), "candidate.jar");
        UpdateService service = new UpdateService(jar, data);
        assertTrue(service.status().contains("pending: none"));
        service.stage(candidate, sums, null, false);
        assertTrue(service.status().contains("pending: 6.0.0"));
        assertTrue(service.status().contains("staged: 6.0.0"));
    }
}
