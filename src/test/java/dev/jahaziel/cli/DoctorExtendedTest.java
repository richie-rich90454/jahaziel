/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Extended doctor checks: runtime, channel, delegated config, data paths, disk and offline. */
class DoctorExtendedTest {
    @TempDir private Path root;

    @BeforeAll
    static void pinSqliteNativeDir() throws Exception {
        if (System.getProperty("org.sqlite.tmpdir") == null) {
            Path dir = Files.createTempDirectory("jahaziel-sqlite-native");
            System.setProperty("org.sqlite.tmpdir", dir.toString());
        }
        Files.createDirectories(Path.of(System.getProperty("org.sqlite.tmpdir")));
    }

    private String savedPath;

    @BeforeEach
    void pinGitOnPath() throws Exception {
        Path bin = Files.createDirectories(root.resolve("bin"));
        Path git = bin.resolve(isWindows() ? "git.exe" : "git");
        Files.createFile(git);
        savedPath = System.getenv("PATH");
        dev.jahaziel.platform.TestEnv.set("PATH", bin.toString());
    }

    @AfterEach
    void restore() {
        Cli.setOffline(false);
        if (savedPath == null) {
            dev.jahaziel.platform.TestEnv.clear("PATH");
        } else {
            dev.jahaziel.platform.TestEnv.set("PATH", savedPath);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static Doctor.Check find(List<Doctor.Check> checks, String name) {
        Doctor.Check found = CliTestSupport.find(checks, name);
        return found;
    }

    private static String print(List<Doctor.Check> checks) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Doctor.print(checks, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private Map<String, String> green() throws Exception {
        Path jar = Files.createDirectories(root.resolve("jar"));
        Files.write(jar.resolve(".env"), CliTestSupport.validEnv().getBytes(StandardCharsets.UTF_8));
        return Cli.parseDotEnv(jar.resolve(".env"));
    }

    private List<Doctor.Check> run(Path jar, Path data, Map<String, String> env) throws Exception {
        return Doctor.runExtended(jar, data, CliTestSupport.freePort(), env, jar.resolve("jahaziel.jar"));
    }

    @Test
    void extendedRunAddsEightChecks() throws Exception {
        Map<String, String> env = green();
        Path jar = root.resolve("jar");
        Path data = jar.resolve("data");
        Files.createDirectories(data);
        Files.createDirectories(data.resolve("tmp"));
        List<Doctor.Check> checks = run(jar, data, env);

        assertEquals(19, checks.size());
        for (int i = 0; i < checks.size(); i++) {
            assertTrue(checks.get(i).ok(), checks.get(i).name() + ": " + checks.get(i).detail());
        }
        String text = print(checks);
        assertTrue(text.contains("doctor: 19/19 checks passed"));
        assertTrue(find(checks, "channel").detail().contains("(pinned)"));
        assertTrue(find(checks, "catalog").detail().contains("pinned catalog checksums verified"));
        assertTrue(find(checks, "idp").detail().contains("identity-provider configuration valid"));
        assertTrue(find(checks, "dataDirs").detail().contains("data locations:"));
        assertTrue(find(checks, "disk").detail().contains("usable bytes"));
        assertTrue(find(checks, "runtime").detail().contains("runtime available"));
        assertTrue(find(checks, "capabilities").detail().contains("tool downloads enabled"));
    }

    @Test
    void catalogMismatchIsReported() throws Exception {
        Map<String, String> env = green();
        Path jar = root.resolve("jar");
        Path data = jar.resolve("data");
        Path catalog = Files.createDirectories(data.resolve("catalog"));
        Files.write(
                catalog.resolve("pinned.sha256"),
                ("missing.bin="
                                + "0000000000000000000000000000000000000000000000000000000000000000"
                                + "\n")
                        .getBytes(StandardCharsets.UTF_8));

        Doctor.Check check = find(run(jar, data, env), "catalog");
        assertFalse(check.ok());
        assertTrue(check.detail().contains("catalog artifact missing.bin is missing"));
        assertTrue(check.fix().contains("restore missing.bin"));
    }

    @Test
    void idpIssuesAreReported() throws Exception {
        Map<String, String> env = green();
        Path jar = root.resolve("jar");
        Path data = jar.resolve("data");
        Map<String, String> bad = new java.util.HashMap<String, String>(env);
        bad.put("JAHAZIEL_IDP_ISSUER", "http://not-https.example");
        bad.put("JAHAZIEL_IDP_CLIENT_SECRET", "change_me");

        Doctor.Check check = find(run(jar, data, bad), "idp");
        assertFalse(check.ok());
        assertTrue(check.detail().contains("issuer must be an https URL"));
        assertTrue(check.detail().contains("client secret is missing or a placeholder"));
    }

    @Test
    void dataSubdirFailureIsReported() throws Exception {
        Path data = Files.createDirectories(root.resolve("data"));
        Files.write(data.resolve("logs"), "not a dir".getBytes(StandardCharsets.UTF_8));

        Doctor.Check check = Doctor.dataSubdirs(data);
        assertFalse(check.ok());
        assertTrue(check.detail().contains("logs"));
        assertTrue(check.fix().contains("fix ownership"));
    }

    @Test
    void diskFailureIsReported() throws Exception {
        Doctor.Check check = Doctor.headroom(root.resolve("does-not-exist"));
        assertFalse(check.ok());
        assertTrue(check.detail().contains("cannot determine disk space"));
    }

    @Test
    void offlineMarksCapabilitiesUnavailable() throws Exception {
        Cli.setOffline(true);
        Doctor.Check check = Doctor.capabilities();
        assertTrue(check.ok());
        assertTrue(check.detail().contains("offline: tool downloads unavailable"));
        assertTrue(check.detail().contains("opencode"));
    }

    @Test
    void runtimeRejectsUnsupportedLinuxArch() {
        String os = System.getProperty("os.name", "");
        String arch = System.getProperty("os.arch", "");
        try {
            System.setProperty("os.name", "Linux");
            System.setProperty("os.arch", "riscv64");
            Doctor.Check check = Doctor.runtime();
            assertFalse(check.ok());
            assertTrue(check.detail().contains("riscv64"));
        } finally {
            System.setProperty("os.name", os);
            System.setProperty("os.arch", arch);
        }
    }
}
