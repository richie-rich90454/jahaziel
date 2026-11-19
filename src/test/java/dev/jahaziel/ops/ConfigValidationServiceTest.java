/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Behavior of {@link ConfigValidationService}. */
class ConfigValidationServiceTest {
    @TempDir Path temp;

    private String sha(String value) {
        return OpsFiles.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> catalog(String name, String content) throws Exception {
        Path dir = temp.resolve("catalog");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
        Map<String, String> expected = new HashMap<String, String>();
        expected.put(name, sha(content));
        return expected;
    }

    private ConfigValidationService.IdpConfig goodIdp() {
        return new ConfigValidationService.IdpConfig(
                "https://idp.example.test", "client-1", "real-secret-value");
    }

    @Test
    void passesValidConfiguration() throws Exception {
        Map<String, String> expected = catalog("catalog.bin", "artifact-1");
        ConfigValidationService service = new ConfigValidationService(temp.resolve("data"));
        List<ConfigValidationService.Issue> issues =
                service.check("Linux", "amd64", expected, temp.resolve("catalog"), goodIdp());
        assertEquals(0, issues.size());
        service.validate("Linux", "amd64", expected, temp.resolve("catalog"), goodIdp());
    }

    @Test
    void failFastOnUnsupportedPlatform() throws Exception {
        Map<String, String> expected = catalog("catalog.bin", "artifact-1");
        final ConfigValidationService service = new ConfigValidationService(temp.resolve("data"));
        ConfigValidationService.ConfigValidationException failure =
                assertThrows(
                        ConfigValidationService.ConfigValidationException.class,
                        new Executable() {
                            @Override
                            public void execute() throws Throwable {
                                service.validate(
                                        "Plan9",
                                        "sparc",
                                        expected,
                                        temp.resolve("catalog"),
                                        goodIdp());
                            }
                        });
        assertTrue(failure.getMessage().contains("platform"));
        assertTrue(failure.getMessage().contains("fix:"));
    }

    @Test
    void rejectsBadChecksumAndIdpAndArch() throws Exception {
        Map<String, String> expected = catalog("catalog.bin", "artifact-1");
        expected.put("catalog.bin", "deadbeef");
        ConfigValidationService.IdpConfig badIdp =
                new ConfigValidationService.IdpConfig("http://insecure", "", "changeme");
        ConfigValidationService service = new ConfigValidationService(temp.resolve("data"));
        List<ConfigValidationService.Issue> issues =
                service.check("Linux", "sparc", expected, temp.resolve("catalog"), badIdp);
        assertTrue(issues.size() >= 4);
        boolean sawArch = false;
        boolean sawChecksum = false;
        boolean sawIdp = false;
        for (int i = 0; i < issues.size(); i++) {
            ConfigValidationService.Issue issue = issues.get(i);
            if (issue.name().equals("platform.arch")) sawArch = true;
            if (issue.name().equals("catalog.catalog.bin")) sawChecksum = true;
            if (issue.name().startsWith("idp.")) sawIdp = true;
            assertTrue(issue.fix().length() > 0);
        }
        assertTrue(sawArch && sawChecksum && sawIdp);
    }

    @Test
    void rejectsCatalogPathEscape() throws Exception {
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("../outside.bin", sha("x"));
        ConfigValidationService service = new ConfigValidationService(temp.resolve("data"));
        List<ConfigValidationService.Issue> issues =
                service.check("Linux", "x86_64", expected, temp.resolve("catalog"), goodIdp());
        assertEquals(1, issues.size());
    }
}
