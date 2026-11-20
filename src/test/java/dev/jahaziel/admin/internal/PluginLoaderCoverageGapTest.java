/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

class PluginLoaderCoverageGapTest {
    @TempDir private Path tmp;

    @Test
    void absentDirMeansNoPlugins() {
        PluginLoader loader = new PluginLoader(tmp.resolve("missing").toString());
        assertTrue(loader.plugins().isEmpty());
        loader.start();
        loader.stop();
    }

    @Test
    void emptyDirMeansNoPlugins() throws Exception {
        Path dir = tmp.resolve("empty");
        Files.createDirectories(dir);
        PluginLoader loader = new PluginLoader(dir.toString());
        assertTrue(loader.plugins().isEmpty());
        loader.start();
        loader.stop();
    }

    @Test
    void nonJarFilesAreIgnored() throws Exception {
        Path dir = tmp.resolve("nojars");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("note.txt"), "hi");
        PluginLoader loader = new PluginLoader(dir.toString());
        assertTrue(loader.plugins().isEmpty());
    }

    private static MockedStatic.Verification isDirectory() {
        return new MockedStatic.Verification() {
            @Override
            public void apply() {
                Files.isDirectory(any(Path.class));
            }
        };
    }

    private static MockedStatic.Verification listFiles() {
        return new MockedStatic.Verification() {
            @Override
            public void apply() throws IOException {
                Files.list(any(Path.class));
            }
        };
    }

    @Test
    void ghostJarLoadsEmpty() {
        Path jar = mock(Path.class);
        when(jar.toString()).thenReturn("ghost.jar");
        when(jar.toUri()).thenReturn(tmp.resolve("ghost.jar").toUri());
        Path txt = mock(Path.class);
        when(txt.toString()).thenReturn("note.txt");
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
            files.when(isDirectory()).thenReturn(true);
            files.when(listFiles()).thenReturn(Stream.of(jar, txt));
            PluginLoader loader = new PluginLoader("whatever");
            assertTrue(loader.plugins().isEmpty());
            loader.start();
            loader.stop();
        }
    }

    @Test
    void listFailureWraps() {
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
            files.when(isDirectory()).thenReturn(true);
            files.when(listFiles()).thenThrow(new IOException("boom"));
            assertThrows(
                    UncheckedIOException.class,
                    new Executable() {
                        @Override
                        public void execute() {
                            new PluginLoader("whatever");
                        }
                    });
        }
    }

    @Test
    void jarUrlFailureWraps() throws Exception {
        Path bad = mock(Path.class);
        when(bad.toString()).thenReturn("evil.jar");
        URI uri = mock(URI.class);
        when(bad.toUri()).thenReturn(uri);
        when(uri.toURL()).thenThrow(new MalformedURLException("boom"));
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
            files.when(isDirectory()).thenReturn(true);
            files.when(listFiles()).thenReturn(Stream.of(bad));
            assertThrows(
                    UncheckedIOException.class,
                    new Executable() {
                        @Override
                        public void execute() {
                            new PluginLoader("whatever");
                        }
                    });
        }
    }
}
