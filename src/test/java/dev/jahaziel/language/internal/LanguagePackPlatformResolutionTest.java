/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class LanguagePackPlatformResolutionTest {
    private static final String OLD_OS = System.getProperty("os.name");
    private static final String OLD_ARCH = System.getProperty("os.arch");

    @TempDir Path temporary;

    @AfterEach
    void restoreHostProperties() {
        restore("os.name", OLD_OS);
        restore("os.arch", OLD_ARCH);
    }

    @Test
    void catalogOsFieldIsResolvedAgainstTheCurrentPlatform() {
        assertTrue(LanguagePackServiceImpl.osMatches("*", Platform.Os.WINDOWS));
        assertTrue(LanguagePackServiceImpl.osMatches("linux", Platform.Os.LINUX));
        assertTrue(LanguagePackServiceImpl.osMatches("darwin", Platform.Os.MACOS));
        assertTrue(LanguagePackServiceImpl.osMatches("macos", Platform.Os.MACOS));
        assertTrue(LanguagePackServiceImpl.osMatches("windows", Platform.Os.WINDOWS));
        assertFalse(LanguagePackServiceImpl.osMatches("linux", Platform.Os.MACOS));
        assertFalse(LanguagePackServiceImpl.osMatches("darwin", Platform.Os.WINDOWS));
    }

    @Test
    void nodeResolutionPrefersExplicitConfigThenBundledTool() throws Exception {
        Path root = temporary.toRealPath();
        Path explicit = root.resolve("configured").resolve("node");
        assertEquals(
                explicit.toAbsolutePath().normalize(),
                LanguagePackServiceImpl.resolveNodeExecutable(explicit.toString(), root));

        Platform.Os os = Platforms.detect(null).os();
        String executable = os == Platform.Os.WINDOWS ? "node.exe" : "node";
        Path bundled =
                os == Platform.Os.WINDOWS
                        ? root.resolve("tools/node").resolve(executable)
                        : root.resolve("tools/node/bin").resolve(executable);
        Files.createDirectories(bundled.getParent());
        Files.writeString(bundled, "node");
        assertTrue(bundled.toFile().setExecutable(true, true));

        assertEquals(bundled, LanguagePackServiceImpl.resolveNodeExecutable("", root));
    }

    @Test
    void unsupportedHostIsReportedExplicitlyAndBlocksSelection() throws Exception {
        System.setProperty("os.name", "Windows 11");
        System.setProperty("os.arch", "amd64");
        LanguagePackServiceImpl service = service();

        String rust = availability(service, "rust");
        String cpp = availability(service, "cpp");
        assertTrue(rust.contains("UNAVAILABLE_ON_THIS_HOST"), rust);
        assertTrue(cpp.contains("UNAVAILABLE_ON_THIS_HOST"), cpp);

        final WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                service.select(workspace, "rust");
                            }
                        });
        assertTrue(failure.getMessage().contains("UNAVAILABLE_ON_THIS_HOST"));
    }

    @Test
    void linuxCatalogKeepsItsExistingAvailabilityContract() {
        System.setProperty("os.name", "Linux");
        System.setProperty("os.arch", "amd64");
        LanguagePackServiceImpl service = service();

        assertEquals("not published for x86_64", availability(service, "rust"));
        assertTrue(availability(service, "cpp").contains("UNAVAILABLE_ON_THIS_HOST"));
    }

    private LanguagePackServiceImpl service() {
        LanguagePackServiceImpl.Definition rust =
                new LanguagePackServiceImpl.Definition(
                        "rust", "Rust Analyzer", "test", "linux", Map.of());
        LanguagePackServiceImpl.Definition cpp =
                new LanguagePackServiceImpl.Definition("cpp", "clangd", "test", "darwin", Map.of());
        LanguagePackServiceImpl.Definition python =
                new LanguagePackServiceImpl.Definition("python", "Pyright", "test", Map.of());
        return new LanguagePackServiceImpl(
                temporary.resolve("cache"),
                mock(HttpClient.class),
                List.of(rust, cpp, python),
                Path.of("missing-node"));
    }

    private static String availability(LanguagePackServiceImpl service, String id) {
        List<LanguagePackService.Pack> catalog = service.catalog();
        for (int index = 0; index < catalog.size(); index++) {
            LanguagePackService.Pack pack = catalog.get(index);
            if (pack.id().equals(id)) return pack.availability();
        }
        return "";
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
