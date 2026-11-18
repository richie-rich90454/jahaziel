/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalPathGuardTest {
    @TempDir Path temporary;

    @Test
    void acceptsContainedFile() throws Exception {
        Path inside = temporary.resolve("notes");
        Files.createDirectories(inside);
        Files.write(inside.resolve("readme.txt"), "hello".getBytes("UTF-8"));

        assertTrue(CanonicalPathGuard.check(temporary, Paths.get("notes", "readme.txt")));
    }

    @Test
    void acceptsMissingFileUnderRoot() {
        assertTrue(CanonicalPathGuard.check(temporary, Paths.get("new", "file.txt")));
    }

    @Test
    void rejectsParentTraversal() {
        assertFalse(CanonicalPathGuard.check(temporary, Paths.get("..", "outside.txt")));
    }

    @Test
    void rejectsPosixAndWindowsShapedAbsolutePaths() {
        assertFalse(CanonicalPathGuard.check(temporary, Paths.get("/etc", "passwd")));
        assertFalse(CanonicalPathGuard.check(temporary, Paths.get("C:\\Windows\\System32")));
    }

    @Test
    void rejectsWindowsDeviceNames() {
        assertFalse(CanonicalPathGuard.check(temporary, Paths.get("CON")));
        assertFalse(CanonicalPathGuard.check(temporary, Paths.get("nul.txt")));
        assertFalse(CanonicalPathGuard.check(temporary, Paths.get("aux", "file.txt")));
    }

    @Test
    void rejectsSymlinkEscapingRoot() throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Assumptions.assumeTrue(!windows);

        Path outside = temporary.resolve("outside");
        Files.createDirectories(outside);
        Files.write(outside.resolve("secret.txt"), "secret".getBytes("UTF-8"));
        Path link = temporary.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException unsupported) {
            Assumptions.abort("symlinks unavailable");
            return;
        } catch (UnsupportedOperationException unsupported) {
            Assumptions.abort("symlinks unavailable");
            return;
        }

        assertFalse(CanonicalPathGuard.check(temporary, Paths.get("link", "secret.txt")));
    }
}
