/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agenttools.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.platform.Platform;

import org.junit.jupiter.api.Test;

import java.util.Map;

class AgentToolPlatformCapabilityTest {
    private static final String[] KEYS = {
        "linux-x64", "linux-arm64", "darwin-x64", "darwin-arm64", "windows-x64"
    };

    private static final String[] OPENCODE_CHECKSUMS = {
        "588310a336fa3b9df36b3f0225b26f679d453efaad0cf3d66c3651ef3ec1647a",
        "fae5be262d693b7c5e562856cc473e59e741772936db0c0600ac0d5ed5bd088b",
        "b0212efa6db347187225a17fa3a31c6b261bf4c9f1589a290b0ca902d720d250",
        "c1c9e62521192192ef4c3ed1355ada2144055bddb61682217e997b03c55203c1",
        "09a6e693157f0887e504f2cf4357a8cbddc5ead5d0dffb9a269dcc47c48640f5"
    };

    private static final String[] RIPGREP_CHECKSUMS = {
        "33e15bcf1624b25cdd2a55813a47a2f95dbe126268203e76aa6a585d1e7b149c",
        "800b1e7206afe799dfb5a6901f23147cfaabe0e52210538100f61e86e1740915",
        "af7825fcc69a2afc7a7aea55fc9af90e26421d8f20fe59df32e233c0b8a231c1",
        "3750b2e93f37e0c692657da574d7019a101c0084da05a790c83fd335bad973e4",
        "71b2fef860abe467217a538ff31de02f5258807c0129f771846f87bd029aafc5"
    };

    @Test
    void capabilityIsPublishedForLinuxMacOsAndWindowsX64() {
        assertTrue(AgentToolServiceImpl.supportsHost(Platform.Os.LINUX, Platform.Arch.X64));
        assertTrue(AgentToolServiceImpl.supportsHost(Platform.Os.LINUX, Platform.Arch.ARM64));
        assertTrue(AgentToolServiceImpl.supportsHost(Platform.Os.MACOS, Platform.Arch.X64));
        assertTrue(AgentToolServiceImpl.supportsHost(Platform.Os.MACOS, Platform.Arch.ARM64));
        assertTrue(AgentToolServiceImpl.supportsHost(Platform.Os.WINDOWS, Platform.Arch.X64));
        assertFalse(AgentToolServiceImpl.supportsHost(Platform.Os.WINDOWS, Platform.Arch.ARM64));

        assertEquals(
                "",
                AgentToolServiceImpl.hostCapabilityMessage(
                        Platform.Os.LINUX, Platform.Arch.X64));
        assertTrue(
                AgentToolServiceImpl.hostCapabilityMessage(
                                Platform.Os.WINDOWS, Platform.Arch.ARM64)
                        .contains("UNAVAILABLE_ON_THIS_HOST"));
    }

    @Test
    void everyPublishedArtifactCarriesAnOfficialPinnedChecksumAndEntryPath() throws Exception {
        Map<String, Object> openCode = artifacts("ARTIFACTS");
        Map<String, Object> ripgrep = artifacts("RIPGREP_ARTIFACTS");
        assertEquals(5, openCode.size());
        assertEquals(5, ripgrep.size());

        for (int index = 0; index < KEYS.length; index++) {
            String key = KEYS[index];
            Object opencodeArtifact = openCode.get(key);
            Object ripgrepArtifact = ripgrep.get(key);
            assertTrue(opencodeArtifact != null, key);
            assertTrue(ripgrepArtifact != null, key);

            assertEquals(OPENCODE_CHECKSUMS[index], property(opencodeArtifact, "sha256"));
            assertEquals(RIPGREP_CHECKSUMS[index], property(ripgrepArtifact, "sha256"));
            assertTrue(property(opencodeArtifact, "sha256").matches("[0-9a-f]{64}"));
            assertTrue(property(ripgrepArtifact, "sha256").matches("[0-9a-f]{64}"));

            assertTrue(property(opencodeArtifact, "executableEntry").length() > 0);
            assertTrue(property(ripgrepArtifact, "executableEntry").length() > 0);
            String format = property(opencodeArtifact, "format");
            assertTrue(format.equals("zip") || format.equals("tar.gz"));
            assertTrue(Long.parseLong(property(opencodeArtifact, "expandedBytes")) > 0);
            assertTrue(Long.parseLong(property(ripgrepArtifact, "expandedBytes")) > 0);
            assertTrue(Long.parseLong(property(opencodeArtifact, "downloadBytes")) > 0);
            assertTrue(Long.parseLong(property(ripgrepArtifact, "downloadBytes")) > 0);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> artifacts(String name) throws Exception {
        java.lang.reflect.Field field = AgentToolServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private static String property(Object artifact, String name) throws Exception {
        java.lang.reflect.Field field = artifact.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(artifact);
        return value == null ? "" : value.toString();
    }
}
